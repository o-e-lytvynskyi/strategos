package dev.strategos.core.diag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RingHistogramTest {

    @Test
    void givesNearestRankPercentilesOfOneToAHundred() {
        RingHistogram h = new RingHistogram(100);
        for (long v = 100; v >= 1; v--) { // recording order must not matter
            h.record(v);
        }
        assertThat(h.count()).isEqualTo(100);
        assertThat(h.percentile(0.5)).isEqualTo(50);
        assertThat(h.percentile(0.95)).isEqualTo(95);
        assertThat(h.percentile(0.99)).isEqualTo(99);
        assertThat(h.percentile(0.07)).isEqualTo(7);
        assertThat(h.percentile(0)).isEqualTo(1);
        assertThat(h.percentile(1)).isEqualTo(100);
        assertThat(h.max()).isEqualTo(100);
        assertThat(h.last()).isEqualTo(1);
    }

    @Test
    void keepsOnlyTheLastSamplesWhenItWraps() {
        RingHistogram h = new RingHistogram(10);
        for (long v = 1; v <= 25; v++) {
            h.record(v);
        }
        assertThat(h.count()).isEqualTo(10);
        assertThat(h.max()).isEqualTo(25);
        assertThat(h.percentile(0)).isEqualTo(16);
        assertThat(h.percentile(0.5)).isEqualTo(20);
        assertThat(h.last()).isEqualTo(25);
    }

    @Test
    void isZeroWhenEmpty() {
        RingHistogram h = new RingHistogram(4);
        assertThat(h.count()).isZero();
        assertThat(h.max()).isZero();
        assertThat(h.percentile(0.5)).isZero();
    }

    @Test
    void rejectsABadCapacityOrQuantile() {
        assertThatThrownBy(() -> new RingHistogram(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RingHistogram(4).percentile(1.5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RingHistogram(4).percentile(Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
