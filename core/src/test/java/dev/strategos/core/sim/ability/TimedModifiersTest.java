package dev.strategos.core.sim.ability;

import static dev.strategos.core.sim.ability.AbilityFixtures.F0;
import static dev.strategos.core.sim.ability.AbilityFixtures.P0;
import static dev.strategos.core.sim.ability.AbilityFixtures.spearman;
import static dev.strategos.core.sim.ability.AbilityFixtures.state;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.CorePaths;
import dev.strategos.core.protocol.Target;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.protocol.UseAbility;
import dev.strategos.core.shared.AbilityId;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.sim.stats.Modifier;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * UNIT-10.3: the timed modifiers an ability gives come off on exactly the tick they were for, are renewed rather than
 * doubled, survive a save mid-effect (the modifiers follow from the definition), replay to the live hash, and cost the
 * state nothing while there are none: a match that never used an ability saves and hashes as it did before them.
 */
class TimedModifiersTest {
    private static void rally(SimHarness h, int unit) {
        h.submit(P0, UseAbility.replace(UnitSet.of(unit), new AbilityId("rally"), Target.none()));
        h.step();
    }

    private static MatchSnapshot roundTrip(MatchSnapshot saved) {
        return MatchSnapshotCodec.decode(
                        new Dynamic<>(JsonOps.INSTANCE, MatchSnapshotCodec.encode(JsonOps.INSTANCE, saved)),
                        Migrations.production())
                .getOrThrow();
    }

    @Test
    void theModifiersComeOffOnTheExactTickAndTheEntryGoesWithThem() {
        SimHarness h = AbilityFixtures.match();
        int a = spearman(h, F0, 10, 16);
        MatchState s = state(h);
        int i = Handle.index(a);
        double base = s.stats().walkSpeed(i);
        rally(h, a);
        long fired = h.tick();
        assertThat(s.play().abilities().timed().expiryOf(a, "rally")).isEqualTo(fired + 40);
        for (long t = fired + 1; t < fired + 40; t++) {
            h.step();
            assertThat(s.stats().walkSpeed(i)).as("tick %d", t).isCloseTo(base * 1.5, within(1e-12));
        }
        h.step();
        assertThat(h.tick()).isEqualTo(fired + 40);
        assertThat(s.stats().walkSpeed(i)).isCloseTo(base, within(1e-12));
        assertThat(s.play().abilities().timed().empty()).isTrue();
        assertThat(s.stats().breakdown(i, CorePaths.WALK_SPEED).toString())
                .as("no source is left behind")
                .doesNotContain("-2147483647");
    }

    @Test
    void renewingAUnitsModifiersReplacesThemAndNeverDoublesThem() {
        SimHarness h = AbilityFixtures.match();
        int a = spearman(h, F0, 10, 16);
        MatchState s = state(h);
        int i = Handle.index(a);
        double base = s.stats().walkSpeed(i);
        int source = AbilityEffects.source(s.templates().abilityIndex("rally"));
        assertThat(source).isEqualTo(Integer.MIN_VALUE + 1 + s.templates().abilityIndex("rally"));
        List<Modifier> mods = List.of(Modifier.mul(CorePaths.WALK_SPEED, 1.5, source));
        s.play().abilities().timed().apply(s.stats(), a, "rally", source, 100, mods);
        s.play().abilities().timed().apply(s.stats(), a, "rally", source, 150, mods);
        assertThat(s.stats().walkSpeed(i)).as("one 1.5, not 2.25").isCloseTo(base * 1.5, within(1e-12));
        assertThat(s.play().abilities().timed().size()).isEqualTo(1);
        assertThat(s.play().abilities().timed().expiryOf(a, "rally")).isEqualTo(150);
    }

    @Test
    void aUnitThatDiesTakesItsEntryAndNothingElse() {
        SimHarness h = AbilityFixtures.match();
        int a = spearman(h, F0, 10, 16);
        int b = spearman(h, F0, 12, 16);
        rally(h, a);
        rally(h, b);
        MatchState s = state(h);
        assertThat(s.play().abilities().timed().size()).isEqualTo(2);
        s.removeUnit(a);
        h.step();
        assertThat(s.play().abilities().timed().size()).isEqualTo(1);
        assertThat(s.play().abilities().timed().expiryOf(b, "rally")).isPositive();
        int again = spearman(h, F0, 10, 16); // the slot of the dead one, a new generation
        assertThat(s.stats().walkSpeed(Handle.index(again)))
                .as("a new unit starts clean")
                .isCloseTo(s.stats().walkSpeed(Handle.index(b)) / 1.5, within(1e-12));
    }

    @Test
    void savedMidEffectAndRestoredItIsTheSameMatchToTheEnd() {
        SimHarness live = AbilityFixtures.match();
        int a = spearman(live, F0, 10, 16);
        rally(live, a);
        live.runTicks(12);
        MatchSnapshot saved = roundTrip(live.core().snapshot());
        assertThat(saved.play().abilities().ids()).contains("rally");
        assertThat(saved.play().abilities().cooldowns()).hasSize(3);
        assertThat(saved.play().abilities().timed()).hasSize(3);
        SimHarness restored = SimHarness.restore(saved, AbilityFixtures.set(AbilityFixtures.ALL));
        assertThat(restored.stateHash()).isEqualTo(live.stateHash());
        int i = Handle.index(a);
        assertThat(state(restored).stats().walkSpeed(i))
                .isEqualTo(state(live).stats().walkSpeed(i));
        for (int t = 0; t < 120; t++) {
            live.step();
            restored.step();
            assertThat(restored.stateHash()).as("tick %d", live.tick()).isEqualTo(live.stateHash());
        }
        assertThat(state(restored).stats().walkSpeed(i))
                .as("the buff ended in both")
                .isEqualTo(state(live).stats().walkSpeed(i));
        assertThat(state(restored).play().abilities().empty()).isTrue();
    }

    @Test
    void aReplayReachesTheLiveHashes() {
        SimHarness h = AbilityFixtures.match();
        int a = spearman(h, F0, 10, 16);
        rally(h, a);
        h.runTicks(60);
        rally(h, a);
        h.runTicks(10);
        assertThat(h.replay().matches()).isTrue();
        assertThat(h.replay().finalHash()).isEqualTo(h.stateHash());
    }

    @Test
    void whileNothingIsOnCooldownOrTimedTheStateSavesAndHashesAsBefore() {
        SimHarness used = AbilityFixtures.match();
        SimHarness idle = AbilityFixtures.match();
        int a = spearman(used, F0, 10, 16);
        spearman(idle, F0, 10, 16);
        long untouched = idle.stateHash();
        JsonObject fresh = MatchSnapshotCodec.encode(
                        JsonOps.INSTANCE, idle.core().snapshot())
                .getAsJsonObject();
        assertThat(fresh.has("play") && fresh.getAsJsonObject("play").has("abilities"))
                .isFalse();
        used.submit(P0, UseAbility.replace(UnitSet.of(a), new AbilityId("mend"), Target.none()));
        used.step();
        assertThat(used.stateHash()).as("a cooldown is state").isNotEqualTo(idle.stateHash());
        used.runTicks(25);
        idle.runTicks(26);
        assertThat(state(used).play().abilities().empty()).isTrue();
        assertThat(used.stateHash())
                .as("the cooldown ran out: as if never used")
                .isEqualTo(idle.stateHash());
        assertThat(untouched).isNotZero();
        JsonObject later = MatchSnapshotCodec.encode(
                        JsonOps.INSTANCE, used.core().snapshot())
                .getAsJsonObject();
        assertThat(later.has("play") && later.getAsJsonObject("play").has("abilities"))
                .isFalse();
    }
}
