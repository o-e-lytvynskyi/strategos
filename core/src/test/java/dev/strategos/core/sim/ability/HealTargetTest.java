package dev.strategos.core.sim.ability;

import static dev.strategos.core.sim.ability.AbilityFixtures.F0;
import static dev.strategos.core.sim.ability.AbilityFixtures.F1;
import static dev.strategos.core.sim.ability.AbilityFixtures.state;
import static dev.strategos.core.sim.ability.HealFixtures.healer;
import static dev.strategos.core.sim.ability.HealFixtures.infantry;
import static dev.strategos.core.sim.ability.HealFixtures.worker;
import static dev.strategos.core.sim.ability.HealFixtures.wound;
import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.AbilityDef;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

/**
 * CMB-12.2: the {@code most_wounded_ally} picker names the ally in range that lost the largest share of its hit points:
 * not the nearest, not the largest wound in hit points; out of range, full, hostile and the healer itself are ignored;
 * on a tie the lower handle.
 */
class HealTargetTest {
    private final AbilityDef def = HealFixtures.heal();

    private int pick(SimHarness h, int healer) {
        return AbilityTargets.pick("most_wounded_ally", state(h), healer, def);
    }

    @Test
    void theLargestShareLostWinsNotTheLargestWoundNorTheNearest() {
        SimHarness h = SimHarness.builder().build();
        int healer = healer(h, F0, 10, 16);
        int nearHurt = infantry(h, F0, 11.5, 16); // 100 hit points, 30 lost: 30 %
        int bigWound = infantry(h, F0, 16, 16); // 40 lost: 40 %
        int small = worker(h, F0, 14, 16); // 50 hit points, 25 lost: 50 %: the largest share, the smallest wound
        wound(h, nearHurt, 30);
        wound(h, bigWound, 40);
        wound(h, small, 25);
        assertThat(pick(h, healer)).isEqualTo(small);
    }

    @Test
    void outOfRangeFullHostileAndTheHealerItselfAreIgnored() {
        SimHarness h = SimHarness.builder().build();
        int healer = healer(h, F0, 10, 16);
        wound(h, healer, 30);
        assertThat(pick(h, healer)).as("nobody but itself").isEqualTo(Handle.NONE);
        int full = infantry(h, F0, 12, 16);
        int far = infantry(h, F0, 30, 16); // 20 away: past 12
        wound(h, far, 90);
        int foe = infantry(h, F1, 13, 16);
        wound(h, foe, 90);
        assertThat(pick(h, healer)).as("full, far and hostile").isEqualTo(Handle.NONE);
        int hurt = infantry(h, F0, 21, 16); // 11 away centre to centre, about 10 gap: in range
        wound(h, hurt, 1);
        assertThat(pick(h, healer)).isEqualTo(hurt);
        assertThat(full).isNotEqualTo(hurt);
    }

    @Test
    void aTieGoesToTheLowerHandleWhateverTheOrderOfTheScan() {
        SimHarness h = SimHarness.builder().build();
        int healer = healer(h, F0, 10, 16);
        int first = infantry(h, F0, 14, 16);
        int second = infantry(h, F0, 7, 16);
        int third = infantry(h, F0, 10, 20);
        for (int u : new int[] {third, second, first}) {
            wound(h, u, 40);
        }
        assertThat(Handle.index(first)).isLessThan(Handle.index(second));
        assertThat(pick(h, healer)).isEqualTo(first);
        wound(h, first, 0);
        assertThat(pick(h, healer)).isEqualTo(second);
    }

    @Test
    void theHealerMendsTheMostWoundedByItselfOneAfterAnother() {
        SimHarness h = SimHarness.builder().build();
        healer(h, F0, 10, 16);
        int light = infantry(h, F0, 12, 16);
        int heavy = infantry(h, F0, 14, 16);
        wound(h, light, 10);
        wound(h, heavy, 60);
        h.runTicks(45); // one pulse, whenever in the first period it came, at the heavier wound
        assertThat(HealFixtures.damage(h, heavy)).isEqualTo(55);
        assertThat(HealFixtures.damage(h, light)).isEqualTo(10);
    }
}
