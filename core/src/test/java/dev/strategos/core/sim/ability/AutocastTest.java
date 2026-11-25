package dev.strategos.core.sim.ability;

import static dev.strategos.core.sim.ability.AbilityFixtures.F0;
import static dev.strategos.core.sim.ability.AbilityFixtures.F1;
import static dev.strategos.core.sim.ability.AbilityFixtures.P0;
import static dev.strategos.core.sim.ability.AbilityFixtures.spearman;
import static dev.strategos.core.sim.ability.AbilityFixtures.state;
import static dev.strategos.core.sim.ability.AbilityFixtures.walker;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.content.AbilityDef;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.UnitHurt;
import dev.strategos.core.testkit.SimHarness;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * UNIT-10.5: an ability with {@code autocast} is tried by its idle owners every {@value AbilitySystem#AUTOCAST_PERIOD}
 * ticks, each at its own offset, in handle order, and a picker chooses the target: {@code self}, or the {@code
 * nearest_enemy_in_range}; a unit with orders, an ability on cooldown or nobody in range casts nothing. The spearman
 * of {@link AbilityFixtures#AUTO}: {@code aura} heals it 5 (cooldown 60), {@code snipe} deals 10 pierce within 6
 * blocks (cooldown 40).
 */
class AutocastTest {
    private static SimHarness match() {
        return SimHarness.builder()
                .templates(AbilityFixtures.set(AbilityFixtures.AUTO))
                .build();
    }

    /** The (tick, victim) of every blow of {@code shooter} over {@code ticks} steps. */
    private static List<String> shots(SimHarness h, int shooter, int ticks) {
        List<String> out = new ArrayList<>();
        for (int k = 0; k < ticks; k++) {
            TickOutputs o = h.step();
            for (var e : o.events()) {
                if (e instanceof UnitHurt hurt && hurt.attacker() == shooter) {
                    out.add(o.tick() + ":" + hurt.handle());
                }
            }
        }
        return out;
    }

    @Test
    void selfIsPickedWithinAPeriodAndTheCooldownPacesIt() {
        SimHarness h = match();
        int a = spearman(h, F0, 10, 16);
        MatchState s = state(h);
        int i = Handle.index(a);
        s.health().setDamage(i, 40);
        long start = h.tick();
        h.runTicks(AbilitySystem.AUTOCAST_PERIOD);
        assertThat(s.health().damage(i)).as("healed once within a period").isEqualTo(35);
        long first = s.play().abilities().readyAt(a, "aura") - 60;
        assertThat(first).isBetween(start + 1, start + AbilitySystem.AUTOCAST_PERIOD);
        h.runTicks(40);
        assertThat(s.health().damage(i))
                .as("not again while the cooldown lasts")
                .isEqualTo(35);
        h.runTicks(30);
        assertThat(s.health().damage(i)).as("again once it ran out").isEqualTo(30);
    }

    @Test
    void theNearestEnemyInRangeIsShotAndOnlyThat() {
        SimHarness h = match();
        int a = spearman(h, F0, 10, 16);
        int far = walker(h, F1, 15, 16); // gap 4.3
        int near = walker(h, F1, 13, 17); // gap about 2.4
        int out = walker(h, F1, 22, 16); // 11.3 away: out of range 6
        int friend = walker(h, F0, 11, 16);
        List<String> shots = shots(h, a, 12);
        assertThat(shots).hasSize(1).first().asString().endsWith(":" + near);
        MatchState s = state(h);
        assertThat(s.health().damage(Handle.index(near))).isPositive();
        assertThat(s.health().damage(Handle.index(far))).isZero();
        assertThat(s.health().damage(Handle.index(out))).isZero();
        assertThat(s.health().damage(Handle.index(friend))).isZero();
        List<String> later = shots(h, a, 100);
        assertThat(later)
                .as("every 40 ticks at the nearest: cooldown 40 at an offset")
                .hasSize(2);
        assertThat(later).allSatisfy(shot -> assertThat(shot).endsWith(":" + near));
    }

    @Test
    void nobodyInRangeAndAUnitWithOrdersCastNothing() {
        SimHarness h = match();
        int a = spearman(h, F0, 10, 16);
        walker(h, F1, 30, 16);
        assertThat(shots(h, a, 40)).isEmpty();
        int busyNear = walker(h, F1, 13, 16);
        h.submit(P0, Move.replace(UnitSet.of(a), new Vec2d(10, 20)));
        h.step();
        List<String> whileWalking = shots(h, a, 15);
        assertThat(whileWalking).as("walking is no idling").isEmpty();
        assertThat(state(h).health().damage(Handle.index(busyNear))).isZero();
        h.runTicks(150);
        assertThat(shots(h, a, 45)).as("idle again: it shoots").isNotEmpty();
    }

    @Test
    void equalNearestTiesGoToTheLowerIndex() {
        SimHarness h = match();
        int a = spearman(h, F0, 10, 16);
        int first = walker(h, F1, 13, 14);
        walker(h, F1, 13, 18); // as far, the higher index
        List<String> shots = shots(h, a, 12);
        assertThat(shots).singleElement().asString().endsWith(":" + first);
    }

    @Test
    void twoMatchesOfTheSameHistoryHashAlike() {
        long[] hashes = new long[2];
        List<List<String>> history = new ArrayList<>();
        for (int run = 0; run < 2; run++) {
            SimHarness h = match();
            int a = spearman(h, F0, 10, 16);
            int b = spearman(h, F0, 12, 20);
            walker(h, F1, 14, 17);
            walker(h, F1, 15, 21);
            List<String> shots = new ArrayList<>();
            shots.addAll(shots(h, a, 150));
            shots.addAll(shots(h, b, 150));
            history.add(shots);
            hashes[run] = h.stateHash();
            assertThat(h.replay().matches()).isTrue();
        }
        assertThat(history.get(0)).isNotEmpty().isEqualTo(history.get(1));
        assertThat(hashes[0]).isEqualTo(hashes[1]);
    }

    @Test
    void aPickerOutsideTheRegistryIsRefused() {
        SimHarness h = match();
        int a = spearman(h, F0, 10, 16);
        AbilityDef def = state(h).templates().ability("snipe");
        assertThatThrownBy(() -> AbilityTargets.pick("luckiest", state(h), a, def))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(AbilityTargets.pick("self", state(h), a, def)).isEqualTo(a);
        assertThat(AbilityTargets.pick("nearest_enemy_in_range", state(h), a, def))
                .isEqualTo(Handle.NONE);
    }
}
