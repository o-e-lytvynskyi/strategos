package dev.strategos.core.sim.capture;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.sim.Allies;
import dev.strategos.core.testkit.FakeAllies;
import org.junit.jupiter.api.Test;

/**
 * CMB-10 acceptance 1 and 2 (L1, M9 spec §2.6): 0 A.D.'s worked examples at our scale (mechanics §1.12), and the rule
 * that a building changes owner only at zero owner points.
 */
class CaptureMathsTest {
    static final Allies NONE = Allies.sameFaction(3);
    static final long CENTRE = 2_000_000; // 2,000 points in thousandths
    static final long REGEN = 30_000; // the civic centre's 30 a second
    static final long INFANTRY = 2_500;

    /** Seconds until the building changes owner, or -1 within {@code limit}. */
    static int secondsToCapture(long total, long regen, long strength, double health, double max, int limit) {
        long[] points = {total, 0, 0};
        long[] effect = {0, CaptureMaths.effect(strength, health, max), 0};
        for (int t = 1; t <= limit; t++) {
            if (CaptureMaths.second(points, 0, effect, regen, NONE) != 0) {
                return t;
            }
        }
        return -1;
    }

    @Test
    void twentyInfantryTakeAnEmptyFullHealthCentreInAboutAHundredSeconds() {
        // 50 a second against 30: 20 net, 2,000 points; the last second takes the remainder before regeneration
        int t = secondsToCapture(CENTRE, REGEN, 20 * INFANTRY, 2_000, 2_000, 1_000);
        assertThat(t).isBetween(98, 100);
    }

    @Test
    void twelveInfantryGainNothingAtFullHealthAndTakeItAtHalf() {
        assertThat(secondsToCapture(CENTRE, REGEN, 12 * INFANTRY, 2_000, 2_000, 2_000))
                .isEqualTo(-1);
        // 30 / (0.1 + 0.45) ≈ 54.5 a second, net ≈ 24.5: about 82 s at our 2,000 points (0 A.D.'s 102 s at 2,500)
        int half = secondsToCapture(CENTRE, REGEN, 12 * INFANTRY, 1_000, 2_000, 1_000);
        assertThat(half).isBetween(80, 83);
    }

    @Test
    void theEffectGrowsAsHealthFalls() {
        assertThat(CaptureMaths.effect(1_000, 100, 100)).isEqualTo(1_000);
        assertThat(CaptureMaths.effect(1_000, 0, 100)).isEqualTo(10_000);
        assertThat(CaptureMaths.effect(1_000, 50, 100)).isEqualTo(1_818);
    }

    @Test
    void aMajorityNeverFlipsItOnlyZeroOwnerPointsDo() {
        long[] points = {400_000, 600_000, 0}; // the attacker holds 60 %
        assertThat(CaptureMaths.second(points, 0, new long[] {0, 0, 0}, 0, NONE))
                .isZero();
        assertThat(CaptureMaths.second(points, 0, new long[] {0, 399_999, 0}, 0, NONE))
                .isZero();
        assertThat(points[0]).isEqualTo(1);
        assertThat(CaptureMaths.second(points, 0, new long[] {0, 1, 0}, 0, NONE))
                .isEqualTo(1);
    }

    @Test
    void atZeroTheFactionWithTheMostPointsTakesItTiesToTheLowest() {
        long[] points = {1_000, 5_000, 5_000};
        assertThat(CaptureMaths.second(points, 0, new long[] {0, 500, 500}, 0, NONE))
                .isEqualTo(1);
    }

    @Test
    void anAllyNeverCapturesAndRegenerationReturnsPointsToTheOwner() {
        long[] points = {CENTRE, 0, 0};
        FakeAllies allies = new FakeAllies(new int[] {0, 0, 1}); // 0 and 1 allied
        CaptureMaths.second(points, 0, new long[] {0, 100_000, 0}, 0, allies);
        assertThat(points[0]).isEqualTo(CENTRE);
        long[] taken = {CENTRE - 50_000, 0, 50_000};
        CaptureMaths.second(taken, 0, new long[] {0, 0, 0}, REGEN, NONE);
        assertThat(taken[0]).isEqualTo(CENTRE - 20_000);
        assertThat(taken[2]).isEqualTo(20_000);
    }
}
