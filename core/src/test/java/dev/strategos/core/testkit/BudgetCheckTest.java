package dev.strategos.core.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

/** PERF-01.1's logic: a budget fails on the p50 or the p99 of a sample, naming the value and the limit. */
class BudgetCheckTest {
    /** 1..100: p50 is 50, p99 is 99. */
    private static final long[] ONE_TO_HUNDRED = LongStream.rangeClosed(1, 100).toArray();

    @Test
    void aSampleAboveTheP50LimitFailsWithBothNumbers() {
        assertThat(new BudgetCheck("tick", 40, 200).violations(ONE_TO_HUNDRED))
                .containsExactly("tick: p50 50 above the limit 40");
    }

    @Test
    void aSampleAboveTheP99LimitFailsWithBothNumbers() {
        assertThat(new BudgetCheck("tick", 60, 90).violations(ONE_TO_HUNDRED))
                .containsExactly("tick: p99 99 above the limit 90");
    }

    @Test
    void aSampleAboveBothLimitsNamesBoth() {
        assertThat(new BudgetCheck("tick", 49, 98).violations(ONE_TO_HUNDRED))
                .containsExactly("tick: p50 50 above the limit 49", "tick: p99 99 above the limit 98");
    }

    @Test
    void aSampleWithinBothLimitsPasses() {
        assertThat(new BudgetCheck("tick", 50, 99).violations(ONE_TO_HUNDRED)).isEmpty();
    }

    @Test
    void percentilesAreNearestRankOverASortedCopy() {
        long[] shuffled = {7, 1, 9, 3, 5};
        assertThat(Percentiles.percentile(shuffled, 0)).isEqualTo(1);
        assertThat(Percentiles.percentile(shuffled, 0.5)).isEqualTo(5);
        assertThat(Percentiles.percentile(shuffled, 0.99)).isEqualTo(9);
        assertThat(Percentiles.percentile(ONE_TO_HUNDRED, 0.07))
                .as("7 % of 100 is rank 7")
                .isEqualTo(7);
        assertThat(shuffled).as("the input is left alone").containsExactly(7, 1, 9, 3, 5);
        assertThatThrownBy(() -> Percentiles.percentile(new long[0], 0.5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Percentiles.percentile(shuffled, 1.5)).isInstanceOf(IllegalArgumentException.class);
    }
}
