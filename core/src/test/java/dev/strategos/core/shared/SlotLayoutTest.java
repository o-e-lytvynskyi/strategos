package dev.strategos.core.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/**
 * ORD-03.1 for the layout (spec §5.9): places in rows across the facing, spaced max(0.8, 4 × radius + 0.1), ⌈√n⌉ to a
 * row, the block centred on the target, every two places at least the spacing apart, and the same numbers for the same
 * inputs.
 */
class SlotLayoutTest {
    @Test
    void spacingAndWidth() {
        assertThat(SlotLayout.spacing(0.35)).isCloseTo(1.5, within(1e-12));
        assertThat(SlotLayout.spacing(0.6)).isCloseTo(2.5, within(1e-12));
        assertThat(SlotLayout.spacing(0.1)).as("never below the least spacing").isEqualTo(0.8);
        assertThat(SlotLayout.width(1)).isEqualTo(1);
        assertThat(SlotLayout.width(2)).isEqualTo(2);
        assertThat(SlotLayout.width(9)).isEqualTo(3);
        assertThat(SlotLayout.width(10)).isEqualTo(4);
        assertThat(SlotLayout.width(50)).isEqualTo(8);
    }

    @Test
    void placesAreCentredSpacedAndInRowsAcrossTheFacing() {
        for (int n = 1; n <= 60; n++) {
            int width = SlotLayout.width(n);
            double spacing = SlotLayout.spacing(0.35);
            double fx = 0.6;
            double fz = 0.8;
            double sumX = 0;
            double sumZ = 0;
            for (int k = 0; k < n; k++) {
                double x = SlotLayout.x(k, n, width, spacing, 10, fx, fz);
                double z = SlotLayout.z(k, n, width, spacing, 20, fx, fz);
                sumX += x;
                sumZ += z;
                for (int j = 0; j < k; j++) {
                    double dx = x - SlotLayout.x(j, n, width, spacing, 10, fx, fz);
                    double dz = z - SlotLayout.z(j, n, width, spacing, 20, fx, fz);
                    assertThat(Math.hypot(dx, dz))
                            .as("places %d and %d of %d", j, k, n)
                            .isGreaterThan(spacing - 1e-9);
                }
                // the depth of a place is its row's: places of one row share it
                if (k % width != 0) {
                    assertThat(SlotLayout.depth(k, n, width, spacing))
                            .isEqualTo(SlotLayout.depth(k - 1, n, width, spacing));
                }
            }
            int rows = (n + width - 1) / width;
            if (n % width == 0) {
                assertThat(sumX / n).as("a full block is centred, n = %d", n).isCloseTo(10, within(1e-9));
                assertThat(sumZ / n).isCloseTo(20, within(1e-9));
            }
            assertThat(SlotLayout.depth(0, n, width, spacing))
                    .as("the front row ahead of the target")
                    .isCloseTo((rows - 1) / 2.0 * spacing, within(1e-12));
        }
    }

    @Test
    void theFrontRowLiesAlongTheFacingAndItsLeftFirst() {
        int n = 4;
        int width = SlotLayout.width(n);
        double spacing = 1;
        // facing +Z: left is -X in these coordinates' (-fz, fx) = (-1, 0)
        assertThat(SlotLayout.z(0, n, width, spacing, 0, 0, 1)).isEqualTo(0.5);
        assertThat(SlotLayout.z(2, n, width, spacing, 0, 0, 1)).isEqualTo(-0.5);
        assertThat(SlotLayout.x(0, n, width, spacing, 0, 0, 1)).isEqualTo(-0.5);
        assertThat(SlotLayout.x(1, n, width, spacing, 0, 0, 1)).isEqualTo(0.5);
    }
}
