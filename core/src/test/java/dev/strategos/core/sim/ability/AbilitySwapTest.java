package dev.strategos.core.sim.ability;

import static dev.strategos.core.sim.ability.AbilityFixtures.F0;
import static dev.strategos.core.sim.ability.AbilityFixtures.F1;
import static dev.strategos.core.sim.ability.AbilityFixtures.P0;
import static dev.strategos.core.sim.ability.AbilityFixtures.spearman;
import static dev.strategos.core.sim.ability.AbilityFixtures.state;
import static dev.strategos.core.sim.ability.AbilityFixtures.walker;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.Target;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.protocol.UseAbility;
import dev.strategos.core.shared.AbilityId;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.ContentSwap;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

/**
 * UNIT-10: an ability's index is its place in the set's id-sorted list, which a cooldown row, a timed entry's source
 * and a queued ability order all depend on. A content swap that would move the index of an ability in use is refused;
 * one that only adds a later-sorting ability goes through and the match goes on as it was; and a restore under a set
 * that has an ability sorting earlier keeps every row and order on its own ability.
 */
class AbilitySwapTest {
    private static final String EARLY = "aaa_early";
    private static final String LATE = "zzz_late";

    private static void swap(SimHarness h, TemplateSet set) {
        h.schedule(h.tick() + 1, new ContentSwap(set));
        h.step();
    }

    private static void order(SimHarness h, int unit, String ability, Target target) {
        h.submit(P0, UseAbility.replace(UnitSet.of(unit), new AbilityId(ability), target));
        h.step();
    }

    @Test
    void aSwapThatMovesTheIndexOfAnAbilityInUseIsRefused() {
        SimHarness h = AbilityFixtures.match();
        int a = spearman(h, F0, 10, 16);
        order(h, a, "mend", Target.none()); // a cooldown: mend is in use
        long before = state(h).templates().hash();
        TemplateSet earlier = AbilityFixtures.set(AbilityFixtures.with(EARLY, AbilityFixtures.NOTHING));
        swap(h, earlier);
        assertThat(state(h).templates().hash()).as("refused: mend would move").isEqualTo(before);
        assertThat(state(h).templates().ability(EARLY)).isNull();
    }

    @Test
    void anAbilityOrderInFlightAlsoHoldsItsIndex() {
        SimHarness h = AbilityFixtures.match();
        h.submit(PlayerSlot.ADMIN, new Grant(F0, "food", 50));
        h.step();
        int a = spearman(h, F0, 4, 16);
        int target = walker(h, F1, 24, 16);
        order(h, a, "strike", Target.unit(target)); // walking: the order names strike's index
        assertThat(state(h).orders().size(Handle.index(a))).isEqualTo(1);
        long before = state(h).templates().hash();
        swap(h, AbilityFixtures.set(AbilityFixtures.with(EARLY, AbilityFixtures.NOTHING)));
        assertThat(state(h).templates().hash()).isEqualTo(before);
        swap(h, AbilityFixtures.set(AbilityFixtures.with(LATE, AbilityFixtures.NOTHING)));
        assertThat(state(h).templates().hash())
                .as("a later-sorting one moves nothing")
                .isNotEqualTo(before);
    }

    @Test
    void aSwapAddingALaterSortingAbilityIsAcceptedAndTheMatchGoesOnEqual() {
        SimHarness swapped = AbilityFixtures.match();
        SimHarness twin = AbilityFixtures.match();
        int a = spearman(swapped, F0, 10, 16);
        spearman(twin, F0, 10, 16);
        for (SimHarness h : new SimHarness[] {swapped, twin}) {
            order(h, a, "rally", Target.none());
            order(h, a, "mend", Target.none());
        }
        long before = state(swapped).templates().hash();
        swap(swapped, AbilityFixtures.set(AbilityFixtures.with(LATE, AbilityFixtures.NOTHING)));
        twin.step();
        assertThat(state(swapped).templates().hash()).isNotEqualTo(before);
        assertThat(state(swapped).templates().ability(LATE)).isNotNull();
        assertThat(swapped.stateHash()).isEqualTo(twin.stateHash());
        MatchState s = state(swapped);
        double walk = s.stats().walkSpeed(Handle.index(a));
        assertThat(walk).isCloseTo(state(twin).stats().walkSpeed(Handle.index(a)), within(1e-12));
        for (int t = 0; t < 120; t++) {
            swapped.step();
            twin.step();
            assertThat(swapped.stateHash()).as("tick %d", swapped.tick()).isEqualTo(twin.stateHash());
        }
        assertThat(s.play().abilities().timed().empty())
                .as("the buff ended, its source found")
                .isTrue();
        assertThat(s.stats().walkSpeed(Handle.index(a))).isLessThan(walk);
    }

    private static MatchSnapshot roundTrip(MatchSnapshot saved) {
        return MatchSnapshotCodec.decode(
                        new Dynamic<>(JsonOps.INSTANCE, MatchSnapshotCodec.encode(JsonOps.INSTANCE, saved)),
                        Migrations.production())
                .getOrThrow();
    }

    @Test
    void aRestoreUnderASetWithAnEarlierAbilityKeepsEveryRowAndOrderOnItsAbility() {
        SimHarness live = AbilityFixtures.match();
        live.submit(PlayerSlot.ADMIN, new Grant(F0, "food", 50));
        live.step();
        int a = spearman(live, F0, 4, 16);
        int b = spearman(live, F0, 4, 20);
        int target = walker(live, F1, 24, 16);
        order(live, a, "rally", Target.none());
        order(live, b, "mend", Target.none());
        order(live, b, "strike", Target.unit(target)); // b walks to its target with strike's index in its order
        live.runTicks(5);
        MatchSnapshot saved = roundTrip(live.core().snapshot());
        int oldStrike = state(live).templates().abilityIndex("strike");
        TemplateSet earlier = AbilityFixtures.set(AbilityFixtures.with(EARLY, AbilityFixtures.NOTHING));
        int newStrike = earlier.abilityIndex("strike");
        assertThat(newStrike).as("an earlier ability moved it").isEqualTo(oldStrike + 1);

        SimHarness restored = SimHarness.restore(saved, earlier);
        MatchState s = state(restored);
        assertThat(s.play().abilities().readyAt(b, "mend"))
                .isEqualTo(state(live).play().abilities().readyAt(b, "mend"))
                .isPositive();
        assertThat(s.play().abilities().timed().expiryOf(a, "rally"))
                .isEqualTo(state(live).play().abilities().timed().expiryOf(a, "rally"))
                .isPositive();
        assertThat(s.stats().walkSpeed(Handle.index(a)))
                .as("the buff came back under the source its new index gives")
                .isCloseTo(state(live).stats().walkSpeed(Handle.index(a)), within(1e-12));
        assertThat(s.orders().slot(Handle.index(b)))
                .as("the order follows its ability")
                .isEqualTo(newStrike);
        assertThat(restored.runUntil(() -> s.health().damage(Handle.index(target)) > 0, 400))
                .as("and still strikes its target")
                .isTrue();
        restored.runTicks(130);
        assertThat(s.play().abilities().timed().empty())
                .as("the buff came off its own source")
                .isTrue();
        assertThat(s.stats().walkSpeed(Handle.index(a)))
                .isCloseTo(
                        state(live)
                                .templates()
                                .value(
                                        state(live).identity().templateIndex(0),
                                        state(live).stats().path("UnitMotion/walkSpeed")),
                        within(1e-12));
    }

    @Test
    void aRestoreUnderASetThatLacksTheAbilityOfAnOrderIsRefused() {
        SimHarness live = AbilityFixtures.match();
        live.submit(PlayerSlot.ADMIN, new Grant(F0, "food", 50));
        live.step();
        int a = spearman(live, F0, 4, 16);
        int target = walker(live, F1, 24, 16);
        order(live, a, "strike", Target.unit(target));
        MatchSnapshot saved = roundTrip(live.core().snapshot());
        TemplateSet without = AbilityFixtures.set("{\"mend\":" + AbilityFixtures.NOTHING + "}");
        assertThatThrownBy(() -> SimHarness.restore(saved, without))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strike")
                .hasMessageContaining("lacks");
    }
}
