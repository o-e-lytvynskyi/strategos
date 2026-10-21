package dev.strategos.core.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HasherTest {

    @Test
    void matchesTheGoldenValuePinnedFromTheSpecFormula() {
        // Computed once from the spec's formula by an independent implementation (M0 spec 5.2).
        long hash = new Hasher(42)
                .putInt(7)
                .putLong(-1)
                .putDouble(1.5)
                .putString("strategos")
                .putBoolean(true)
                .finish();
        assertThat(hash).isEqualTo(2753528023466283727L);
    }

    @Test
    void isOrderSensitive() {
        assertThat(new Hasher(1).putLong(1).putLong(2).finish())
                .isNotEqualTo(new Hasher(1).putLong(2).putLong(1).finish());
    }

    @Test
    void seedChangesTheHash() {
        assertThat(new Hasher(1).putLong(5).finish())
                .isNotEqualTo(new Hasher(2).putLong(5).finish());
    }

    @Test
    void everyNanHashesAlike() {
        double otherNan = Double.longBitsToDouble(0x7ff0000000000123L);
        assertThat(Double.isNaN(otherNan)).isTrue();
        assertThat(new Hasher(1).putDouble(otherNan).finish())
                .isEqualTo(new Hasher(1).putDouble(Double.NaN).finish());
    }

    @Test
    void negativeZeroDiffersFromZero() {
        assertThat(new Hasher(1).putDouble(-0.0).finish())
                .isNotEqualTo(new Hasher(1).putDouble(0.0).finish());
    }

    @Test
    void stringsHashTheirLength() {
        assertThat(new Hasher(1).putString("ab").putString("").finish())
                .isNotEqualTo(new Hasher(1).putString("a").putString("b").finish());
    }
}
