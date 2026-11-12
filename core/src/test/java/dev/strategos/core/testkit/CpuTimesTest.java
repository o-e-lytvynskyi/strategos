package dev.strategos.core.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CpuTimesTest {
    @Test
    void subtractsOwnProcessFromBusySystemTimeAcrossAllCores() {
        assertThat(new CpuTimes(1_000, 400, 200).foreignUntil(new CpuTimes(2_000, 800, 400)))
                .isEqualTo(0.4);
    }

    @Test
    void usesOneWholeWindowRatherThanAveragingUnequalSamplingIntervals() {
        CpuTimes start = new CpuTimes(0, 0, 0);
        assertThat(start.foreignUntil(new CpuTimes(100, 0, 0))).isEqualTo(1);
        assertThat(start.foreignUntil(new CpuTimes(1_000, 900, 0))).isEqualTo(0.1);
    }

    @Test
    void toleratesSmallCounterQuantizationWithoutNegativeForeignLoad() {
        assertThat(new CpuTimes(0, 0, 0).foreignUntil(new CpuTimes(100, 60, 41)))
                .isZero();
    }

    @Test
    void rejectsMissingOrRegressingMeasurementCounters() {
        CpuTimes start = new CpuTimes(100, 50, 20);
        assertThatThrownBy(() -> start.foreignUntil(start)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> start.foreignUntil(new CpuTimes(90, 50, 20)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> start.foreignUntil(new CpuTimes(200, 40, 30)))
                .isInstanceOf(IllegalStateException.class);
    }
}
