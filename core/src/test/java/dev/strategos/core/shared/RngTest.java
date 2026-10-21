package dev.strategos.core.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RngTest {

    @Test
    void matchesTheGoldenValuesPinnedFromTheSpecFormula() {
        // Computed once from the spec's formula by an independent implementation (M0 spec 5.2); a change is a
        // determinism break, never a value to update.
        assertThat(Rng.u64(0, 0, 0, 0, 0)).isEqualTo(8756959122742204754L);
        assertThat(Rng.u64(42, 1, 100, 7, 3)).isEqualTo(6630936460945451634L);
        assertThat(Rng.u64(-1, 15, Long.MAX_VALUE, -5, Long.MIN_VALUE)).isEqualTo(-1547883492625338833L);
        assertThat(Rng.mix64(1)).isEqualTo(6238072747940578789L);
    }

    @Test
    void equalCoordinatesGiveEqualDraws() {
        assertThat(Rng.u64(42, 1, 100, 7, 3)).isEqualTo(Rng.u64(42, 1, 100, 7, 3));
        assertThat(Rng.unit(42, 1, 100, 7, 3)).isEqualTo(Rng.unit(42, 1, 100, 7, 3));
    }

    @Test
    void everyCoordinateChangesTheDraw() {
        long base = Rng.u64(42, 1, 100, 7, 3);
        assertThat(Rng.u64(43, 1, 100, 7, 3)).isNotEqualTo(base);
        assertThat(Rng.u64(42, 2, 100, 7, 3)).isNotEqualTo(base);
        assertThat(Rng.u64(42, 1, 101, 7, 3)).isNotEqualTo(base);
        assertThat(Rng.u64(42, 1, 100, 8, 3)).isNotEqualTo(base);
        assertThat(Rng.u64(42, 1, 100, 7, 4)).isNotEqualTo(base);
    }

    @Test
    void unitStaysInTheHalfOpenUnitInterval() {
        assertThat(Rng.unit(42, 1, 100, 7, 3)).isEqualTo(0.35946378582852);
        for (long key = 0; key < 10_000; key++) {
            assertThat(Rng.unit(7, 0, 0, key, 0)).isGreaterThanOrEqualTo(0.0).isLessThan(1.0);
        }
    }

    @Test
    void belowStaysInRange() {
        for (long key = 0; key < 10_000; key++) {
            assertThat(Rng.below(7, 0, 0, key, 0, 3)).isBetween(0, 2);
        }
        assertThat(Rng.below(7, 0, 0, 0, 0, 1)).isZero();
        assertThatThrownBy(() -> Rng.below(7, 0, 0, 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void belowIsUniformByChiSquare() {
        int[] buckets = new int[10];
        for (long key = 0; key < 10_000; key++) {
            buckets[Rng.below(42, 0, 0, key, 0, 10)]++;
        }
        double chiSquare = 0;
        for (int observed : buckets) {
            double diff = observed - 1000.0;
            chiSquare += diff * diff / 1000.0;
        }
        assertThat(chiSquare)
                .as("chi-square with 9 degrees of freedom, p = 0.01")
                .isLessThan(21.67);
    }
}
