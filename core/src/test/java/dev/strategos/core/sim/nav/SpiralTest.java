package dev.strategos.core.sim.nav;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The spiral slot snapping and the stuck ladder search in: every column within the range's circle once, by distance,
 * then Z, then X.
 */
class SpiralTest {

    @Test
    void columnsComeByDistanceThenZThenX() {
        assertThat(Spiral.of(1)).containsExactly(0, 0, 0, -1, -1, 0, 1, 0, 0, 1);
        assertThat(Spiral.of(2)).startsWith(0, 0, 0, -1, -1, 0, 1, 0, 0, 1, -1, -1, 1, -1, -1, 1, 1, 1, 0, -2);
    }

    @Test
    void aRangeHoldsEveryColumnWithinItOnce() {
        int[] spiral = Spiral.of(3);

        assertThat(spiral).hasSize(2 * 29);
        for (int k = 0; k < spiral.length; k += 2) {
            int d = spiral[k] * spiral[k] + spiral[k + 1] * spiral[k + 1];
            assertThat(d).isLessThanOrEqualTo(9);
            if (k > 0) {
                int before = spiral[k - 2] * spiral[k - 2] + spiral[k - 1] * spiral[k - 1];
                assertThat(d).isGreaterThanOrEqualTo(before);
            }
            for (int j = 0; j < k; j += 2) {
                assertThat(spiral[j] == spiral[k] && spiral[j + 1] == spiral[k + 1])
                        .as("column %s, %s twice", spiral[k], spiral[k + 1])
                        .isFalse();
            }
        }
    }
}
