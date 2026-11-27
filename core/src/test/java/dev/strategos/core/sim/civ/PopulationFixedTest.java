package dev.strategos.core.sim.civ;

import static org.assertj.core.api.Assertions.assertThat;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.NoticeKind;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.Train;
import dev.strategos.core.shared.CivId;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.Notice;
import dev.strategos.core.sim.PlayRules;
import dev.strategos.core.sim.nav.PageCoord;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.sim.replicate.ReplicationService;
import dev.strategos.core.testkit.RandomCommands;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * CIV-05: the population rule is the one source of the cap. {@code fixed{cap}} is {@code min(cap, match max)} with
 * housing ignored and refuses with POPULATION_CAP; the default keeps {@code min(housing + base, max)} and HOUSING_NEEDED;
 * the HUD's cap and the AI's read are the rule's; a match with the variant replays and restores to the live hashes.
 */
class PopulationFixedTest {
    private static final MatchRules PLAIN =
            MatchRules.DEFAULTS.withPlay(new PlayRules(PlayRules.Victory.NONE, false, true, false));
    private static final List<CivId> LEAN_VS_DEFAULT = List.of(CivScene.LEAN, CivId.DEFAULT);

    private static SimHarness match(String population) {
        return match(population, VoxelFixtures.flat(64, 64, 32, 1));
    }

    private static SimHarness match(String population, VoxelWorld world) {
        TemplateSet set = CivScene.with(TestRules.civ("{\"population\":" + population + "}"));
        return CivScene.match(set, LEAN_VS_DEFAULT, RuleCatalogue.DEFAULT, PLAIN, world);
    }

    /** A save and a load: the snapshot through its codec, encoded to JSON and decoded again. */
    private static MatchSnapshot roundTrip(MatchSnapshot saved) {
        return MatchSnapshotCodec.decode(
                        new Dynamic<>(JsonOps.INSTANCE, MatchSnapshotCodec.encode(JsonOps.INSTANCE, saved)),
                        Migrations.production())
                .getOrThrow();
    }

    /** A restore of {@code h}'s snapshot, its voxel pages drawn from {@code world}. */
    private static SimHarness restored(SimHarness h, VoxelWorld world) {
        MatchSnapshot snapshot = roundTrip(h.core().snapshot());
        Map<Long, byte[]> slices = new TreeMap<>();
        for (var page : snapshot.pages()) {
            slices.put(
                    page.slice(), world.slice(new PageCoord(page.x(), page.z())).encode());
        }
        return SimHarness.restore(snapshot, KernelAccess.state(h.core()).templates(), slices::get);
    }

    private static void spawn(SimHarness h, int faction, int count) {
        h.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(
                        CivScene.WORKER,
                        new FactionSlot(faction),
                        new Vec2d(faction == 0 ? 20 : 40, faction == 0 ? 20 : 40),
                        count,
                        4));
        h.step();
    }

    private static long notices(SimHarness h, int faction, NoticeKind kind) {
        return h.events(Notice.class).stream()
                .filter(n -> n.kind() == kind && n.faction().value() == faction)
                .count();
    }

    @Test
    void theCapIsTheDataAndHousingIsIgnored() {
        SimHarness h = match("{\"id\":\"fixed\",\"params\":{\"cap\":8}}");
        MatchState s = KernelAccess.state(h.core());

        assertThat(s.civs().rules(0).population()).isNotSameAs(PopulationRule.DEFAULT);
        assertThat(s.population().housing(0)).as("the centre houses 20").isEqualTo(20);
        assertThat(s.populationCap(0)).as("and the cap is 8").isEqualTo(8);
        assertThat(s.populationCap(1)).as("the default faction").isEqualTo(20);
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CivScene.HOUSE, new FactionSlot(0), 20, 20, 0));
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CivScene.HOUSE, new FactionSlot(1), 40, 20, 0));
        h.step();
        assertThat(s.population().housing(0)).isEqualTo(25);
        assertThat(s.populationCap(0)).as("a house adds nothing").isEqualTo(8);
        assertThat(s.populationCap(1)).as("but does to the default").isEqualTo(25);
    }

    @Test
    void theMatchMaximumStillCaps() {
        SimHarness h = match("{\"id\":\"fixed\",\"params\":{\"cap\":500}}");
        MatchState s = KernelAccess.state(h.core());
        int max = s.population().max();

        assertThat(s.populationCap(0)).isEqualTo(Math.min(500, max));
        s.population().setMax(12);
        assertThat(s.populationCap(0)).isEqualTo(12);
        s.population().setMax(3);
        assertThat(s.populationCap(0)).isEqualTo(3);
        assertThat(s.populationCap(1)).as("the default's too").isEqualTo(3);
    }

    @Test
    void theFixedCapRefusesProductionWithItsOwnNoticeOnce() {
        SimHarness h = match("{\"id\":\"fixed\",\"params\":{\"cap\":8}}");
        spawn(h, 0, 7);
        int centre = CivScene.building(h, 0, CivScene.CENTRE, 2, 2);

        h.submit(new PlayerSlot(0), new Train(centre, CivScene.WORKER));
        h.runTicks(5);
        MatchState s = KernelAccess.state(h.core());
        assertThat(s.population().used(0))
                .as("seven and the head's reservation")
                .isEqualTo(8);
        h.submit(new PlayerSlot(0), new Train(centre, CivScene.WORKER));
        h.runTicks(600);
        assertThat(s.population().used(0))
                .as("the first stepped out; the second waits at 8")
                .isEqualTo(8);
        assertThat(notices(h, 0, NoticeKind.POPULATION_CAP))
                .as("one notice, at the cap")
                .isEqualTo(1);
        assertThat(notices(h, 0, NoticeKind.HOUSING_NEEDED)).isZero();

        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CivScene.HOUSE, new FactionSlot(0), 20, 20, 0));
        h.runTicks(600);
        assertThat(s.population().used(0)).as("a house does not free it").isEqualTo(8);
        assertThat(h.invariantViolations()).isEmpty();
    }

    @Test
    void theDefaultKeepsHousingNeeded() {
        SimHarness h = match("{\"id\":\"fixed\",\"params\":{\"cap\":8}}");
        spawn(h, 1, 20);
        int centre = CivScene.building(h, 1, CivScene.CENTRE, 50, 50);

        h.submit(new PlayerSlot(1), new Train(centre, CivScene.WORKER));
        h.runTicks(50);

        assertThat(notices(h, 1, NoticeKind.HOUSING_NEEDED)).isEqualTo(1);
        assertThat(notices(h, 1, NoticeKind.POPULATION_CAP)).isZero();
    }

    @Test
    void theDefaultTakesABaseFromData() {
        SimHarness h = match("{\"id\":\"default\",\"params\":{\"base\":5}}");
        MatchState s = KernelAccess.state(h.core());

        assertThat(s.populationCap(0)).as("20 housed + 5").isEqualTo(25);
        assertThat(s.populationCap(1)).isEqualTo(20);
        assertThat(PopulationRule.DEFAULT.cap(s.population(), 0))
                .as("no base, the default")
                .isEqualTo(20);
    }

    @Test
    void whatTheHudAndTheAiReadIsTheRulesCap() {
        SimHarness h = match("{\"id\":\"fixed\",\"params\":{\"cap\":8}}");

        assertThat(h.queries().populationCap(new FactionSlot(0))).isEqualTo(8);
        assertThat(h.queries().populationCap(new FactionSlot(1))).isEqualTo(20);
        assertThat(ReplicationService.factionState(h.queries(), 1, new FactionSlot(0))
                        .populationCap())
                .isEqualTo(8);
    }

    @Test
    void aMatchWithTheVariantReplaysAndRestoresToTheLiveHashes() {
        VoxelWorld world = VoxelFixtures.flat(64, 64, 32, 1);
        SimHarness h = match("{\"id\":\"fixed\",\"params\":{\"cap\":8}}", world);
        MatchState s = KernelAccess.state(h.core());
        spawn(h, 0, 6);
        SimHarness back = null;
        for (int t = 0; t < 900; t++) {
            for (int f = 0; f < 2; f++) {
                for (var c : RandomCommands.next(7, h.tick(), h.queries(), new FactionSlot(f))) {
                    h.submit(new PlayerSlot(f), c);
                    if (back != null) {
                        back.submit(new PlayerSlot(f), c);
                    }
                }
            }
            h.step();
            if (back != null) {
                back.step();
                assertThat(back.stateHash()).as("tick %d", h.tick()).isEqualTo(h.stateHash());
            }
            if (t == 400) {
                back = restored(h, world);
                assertThat(back.stateHash()).isEqualTo(h.stateHash());
            }
        }
        assertThat(s.population().used(0)).isLessThanOrEqualTo(8 + 6);
        var replay = h.replay();
        assertThat(replay.matches()).isTrue();
        assertThat(replay.finalHash()).isEqualTo(h.stateHash());
    }
}
