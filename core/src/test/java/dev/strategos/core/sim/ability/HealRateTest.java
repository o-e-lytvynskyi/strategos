package dev.strategos.core.sim.ability;

import static dev.strategos.core.sim.ability.AbilityFixtures.F0;
import static dev.strategos.core.sim.ability.HealFixtures.damage;
import static dev.strategos.core.sim.ability.HealFixtures.healer;
import static dev.strategos.core.sim.ability.HealFixtures.infantry;
import static dev.strategos.core.sim.ability.HealFixtures.wound;
import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.AbilityDef;
import dev.strategos.core.content.AbilityKinds;
import dev.strategos.core.testkit.SimHarness;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CMB-12.1: the healer's heal matches the data: its amount a use, its cooldown, so its rate a second; a wounded soldier
 * beside it is healed by autocast, one pulse at a time, never above its maximum hit points.
 */
class HealRateTest {
    private static final int SECOND = 20;

    @Test
    void theHealAbilityIsTheDataOfTheHealer() {
        AbilityDef def = HealFixtures.heal();
        assertThat(def.autocast()).isTrue();
        assertThat(def.pick()).isEqualTo(AbilityKinds.MOST_WOUNDED_ALLY);
        assertThat(def.aim()).isEqualTo(AbilityDef.Aim.UNIT);
        assertThat(def.range()).isEqualTo(12);
        assertThat(def.cooldown()).isEqualTo(40);
        assertThat(HealFixtures.amount()).isEqualTo(5);
        assertThat(HealFixtures.amount() * SECOND / def.cooldown())
                .as("hit points a second: 0 A.D.'s 5 every 2 s")
                .isEqualTo(2.5);
    }

    @Test
    void aWoundedSoldierGainsTheDataAmountEveryCooldownAndStopsAtItsMost() {
        SimHarness h = SimHarness.builder().build();
        healer(h, F0, 10, 16);
        int soldier = infantry(h, F0, 13, 16);
        wound(h, soldier, 23);
        double previous = 23;
        List<Long> at = new ArrayList<>();
        for (int t = 0; t < 400; t++) {
            h.step();
            double now = damage(h, soldier);
            assertThat(now).as("never above its most hit points").isBetween(0.0, 23.0);
            if (now != previous) {
                assertThat(previous - now).isEqualTo(Math.min(HealFixtures.amount(), previous));
                at.add(h.tick());
                previous = now;
            }
        }
        assertThat(at).as("23 damage: 5, 5, 5, 5, then the last 3").hasSize(5);
        for (int k = 1; k < at.size(); k++) {
            assertThat(at.get(k) - at.get(k - 1))
                    .as("a pulse every cooldown")
                    .isGreaterThanOrEqualTo(HealFixtures.heal().cooldown());
        }
        assertThat(damage(h, soldier)).isZero();
        h.runTicks(200);
        assertThat(damage(h, soldier)).as("nothing past full").isZero();
    }

    @Test
    void theRatePerSecondOverALongWoundMatchesTheData() {
        SimHarness h = SimHarness.builder().build();
        healer(h, F0, 10, 16);
        int soldier = infantry(h, F0, 13, 16);
        wound(h, soldier, 90);
        h.runTicks(20 * SECOND);
        double healed = 90 - damage(h, soldier);
        double expected = 2.5 * 20; // the rate a second over twenty seconds, less at most one pulse of start-up
        assertThat(healed).isBetween(expected - HealFixtures.amount(), expected);
    }
}
