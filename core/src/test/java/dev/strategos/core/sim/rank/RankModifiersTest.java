package dev.strategos.core.sim.rank;

import static dev.strategos.core.sim.rank.RankFixtures.F0;
import static dev.strategos.core.sim.rank.RankFixtures.F1;
import static dev.strategos.core.sim.rank.RankFixtures.killWorkers;
import static dev.strategos.core.sim.rank.RankFixtures.spawn;
import static dev.strategos.core.sim.rank.RankFixtures.spearman;
import static dev.strategos.core.sim.rank.RankFixtures.state;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.ContentSwap;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

/**
 * CMB-11.2: a rank's modifier set applies to the unit as personal modifiers, REPLACES the rank before it (rank 3 is
 * 1.15 times the template's, not 1.05 x 1.10 x 1.15), keeps the unit's hit-point ratio when its most changes, survives a
 * save and a restore, replays to the live hash, and costs nothing while no unit has earned anything.
 */
class RankModifiersTest {
    private static final double HP = 100;
    private static final double HACK = 3;

    private static MatchState s(SimHarness h) {
        return state(h);
    }

    private static MatchSnapshot roundTrip(MatchSnapshot saved) {
        return MatchSnapshotCodec.decode(
                        new Dynamic<>(JsonOps.INSTANCE, MatchSnapshotCodec.encode(JsonOps.INSTANCE, saved)),
                        Migrations.production())
                .getOrThrow();
    }

    /** Earns {@code points} whole points at once: a hit worth that much of a fresh worker's value of 60. */
    private static void earn(SimHarness h, int unit, int points) {
        int worker = spawn(h, TemplateSet.WORKER, F1, 11, 16);
        Ranks.credit(s(h), unit, worker, 50.0 * points / 60, 50);
    }

    @Test
    void aRankGivesItsSetAndTheNextRankReplacesItNeverCompounding() {
        SimHarness h = RankFixtures.match();
        int a = spearman(h, F0, 10, 16);
        int i = Handle.index(a);
        assertThat(s(h).stats().maxHealth(i)).isEqualTo(HP);
        earn(h, a, 200);
        assertThat(s(h).stats().maxHealth(i)).isCloseTo(HP * 1.05, within(1e-9));
        assertThat(s(h).stats().meleeDamage(i, 0)).isCloseTo(HACK * 1.05, within(1e-9));
        earn(h, a, 300);
        assertThat(s(h).play().experience().rank(i)).isEqualTo(2);
        assertThat(s(h).stats().maxHealth(i)).as("1.10, not 1.05 x 1.10").isCloseTo(HP * 1.10, within(1e-9));
        assertThat(s(h).stats().meleeDamage(i, 0)).isCloseTo(HACK * 1.10, within(1e-9));
        earn(h, a, 500);
        assertThat(s(h).stats().maxHealth(i)).as("1.15, not 1.05 x 1.10 x 1.15").isCloseTo(HP * 1.15, within(1e-9));
        assertThat(s(h).stats().meleeDamage(i, 1))
                .as("pierce is derived from hack")
                .isCloseTo(HACK * 1.15, within(1e-9));
        int other = spearman(h, F0, 10, 20);
        assertThat(s(h).stats().maxHealth(Handle.index(other)))
                .as("the rank is the unit's own")
                .isEqualTo(HP);
    }

    @Test
    void theHitPointRatioIsKeptWhenTheMostChanges() {
        SimHarness h = RankFixtures.match();
        int a = spearman(h, F0, 10, 16);
        int i = Handle.index(a);
        s(h).health().setDamage(i, 50); // half
        earn(h, a, 200);
        double max = s(h).stats().maxHealth(i);
        assertThat(max).isCloseTo(105, within(1e-9));
        assertThat(max - s(h).health().damage(i)).as("still half").isCloseTo(max / 2, within(1e-9));
        earn(h, a, 300);
        max = s(h).stats().maxHealth(i);
        assertThat(max - s(h).health().damage(i)).as("and again").isCloseTo(max / 2, within(1e-9));
    }

    @Test
    void savedAndRestoredThroughTheCodecItIsTheSameMatchToTheEnd() {
        SimHarness live = RankFixtures.match();
        int a = spearman(live, F0, 10, 16);
        killWorkers(live, a, 9); // 540: rank 2
        s(live).health().setDamage(Handle.index(a), 30);
        MatchSnapshot saved = roundTrip(live.core().snapshot());
        assertThat(saved.play().experience().earned()).containsExactly((long) a, 540_000L);
        assertThat(saved.play().experience().ranks()).containsExactly((long) a, 2L);
        SimHarness restored = SimHarness.restore(saved, RankFixtures.set());
        assertThat(restored.stateHash()).isEqualTo(live.stateHash());
        int i = Handle.index(a);
        assertThat(s(restored).play().experience().rank(i)).isEqualTo(2);
        assertThat(s(restored).play().experience().experience(i)).isEqualTo(540_000);
        assertThat(s(restored).stats().maxHealth(i)).isCloseTo(HP * 1.10, within(1e-9));
        assertThat(s(restored).health().damage(i))
                .as("restore does not rescale it")
                .isEqualTo(30);
        killWorkers(live, a, 8); // 1020: rank 3
        killWorkers(restored, a, 8);
        assertThat(restored.stateHash()).isEqualTo(live.stateHash());
        assertThat(s(restored).stats().maxHealth(i)).isCloseTo(HP * 1.15, within(1e-9));
    }

    @Test
    void aReplayReachesTheLiveHashes() {
        SimHarness h = RankFixtures.match();
        int a = spearman(h, F0, 10, 16);
        killWorkers(h, a, 5);
        h.runTicks(10);
        assertThat(s(h).play().experience().rank(Handle.index(a))).isEqualTo(1);
        assertThat(h.replay().matches()).isTrue();
        assertThat(h.replay().finalHash()).isEqualTo(h.stateHash());
    }

    @Test
    void whileNobodyHasEarnedAnythingTheStateSavesAndHashesAsBefore() {
        SimHarness fought = RankFixtures.match();
        SimHarness idle = RankFixtures.match();
        int a = spearman(fought, F0, 10, 16);
        spearman(idle, F0, 10, 16);
        JsonObject fresh = MatchSnapshotCodec.encode(
                        JsonOps.INSTANCE, idle.core().snapshot())
                .getAsJsonObject();
        assertThat(fresh.has("play") && fresh.getAsJsonObject("play").has("experience"))
                .isFalse();
        long before = fought.stateHash();
        assertThat(before).isEqualTo(idle.stateHash());
        killWorkers(fought, a, 1);
        JsonObject later = MatchSnapshotCodec.encode(
                        JsonOps.INSTANCE, fought.core().snapshot())
                .getAsJsonObject();
        assertThat(later.getAsJsonObject("play").has("experience"))
                .as("experience is state")
                .isTrue();
        s(fought).removeUnit(a);
        assertThat(s(fought).play().experience().empty()).isTrue();
    }

    private static TemplateSet withRanks(JsonArray ranks) {
        return RankFixtures.set((id, json) -> {
            if (id.equals(TemplateSet.INFANTRY)) {
                JsonArray thresholds = new JsonArray();
                for (int k = 0; k < ranks.size(); k++) {
                    thresholds.add(200 + 300 * k);
                }
                json.getAsJsonObject("Promotion").add("thresholds", thresholds);
                json.getAsJsonObject("Promotion").add("ranks", ranks);
            }
        });
    }

    private static SimHarness swapped(TemplateSet second, TemplateSet third) {
        SimHarness h = RankFixtures.match();
        int a = spearman(h, F0, 10, 16);
        earn(h, a, 500); // rank 2
        h.schedule(h.tick() + 1, new ContentSwap(second));
        h.step();
        h.schedule(h.tick() + 1, new ContentSwap(third));
        h.step();
        h.runTicks(5);
        return h;
    }

    @Test
    void aContentSwapGivesRankedUnitsTheNewSetAndAShorterListKeepsTheHighestOne() {
        String hp = "{\"path\":\"Health/max\",\"op\":\"mul\",\"value\":";
        TemplateSet three = withRanks(JsonParser.parseString("[[" + hp + "2}],[" + hp + "3}],[" + hp + "4}]]")
                .getAsJsonArray());
        TemplateSet one = withRanks(JsonParser.parseString("[[" + hp + "5}]]").getAsJsonArray());
        SimHarness h = RankFixtures.match();
        int a = spearman(h, F0, 10, 16);
        int i = Handle.index(a);
        earn(h, a, 500); // rank 2
        assertThat(s(h).stats().maxHealth(i)).isCloseTo(HP * 1.10, within(1e-9));
        h.schedule(h.tick() + 1, new ContentSwap(three));
        h.step();
        assertThat(s(h).stats().maxHealth(i))
                .as("rank 2 of the new set, the old one off")
                .isCloseTo(HP * 3, within(1e-9));
        h.schedule(h.tick() + 1, new ContentSwap(one));
        h.step();
        assertThat(s(h).stats().maxHealth(i))
                .as("a list of one: the highest set it has")
                .isCloseTo(HP * 5, within(1e-9));
        assertThat(s(h).play().experience().rank(i)).as("the rank is as earned").isEqualTo(2);
        h.runTicks(5);
        assertThat(swapped(three, one).stateHash())
                .as("the same swaps, the same hash")
                .isEqualTo(h.stateHash());
        SimHarness restored = SimHarness.restore(roundTrip(h.core().snapshot()), one);
        assertThat(restored.stateHash()).as("a restore on the swapped set").isEqualTo(h.stateHash());
    }
}
