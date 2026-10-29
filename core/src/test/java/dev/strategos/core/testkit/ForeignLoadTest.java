package dev.strategos.core.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.strategos.core.testkit.ForeignLoad.Assessment;
import dev.strategos.core.testkit.ForeignLoad.Reading;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ForeignLoadTest {
    private static final long SECOND = 1_000_000_000L;
    private static final long TICKS = 10_000; // total counter ticks per second in these fixtures

    /** One reading a second; {@code foreign[i]} is the foreign share (in ticks of {@link #TICKS}) of second i. */
    private static ForeignLoad seconds(long... foreign) {
        List<Reading> readings = new ArrayList<>();
        long total = 0;
        long idle = 0;
        readings.add(new Reading(0, new CpuTimes(0, 0, 0)));
        for (int i = 0; i < foreign.length; i++) {
            total += TICKS;
            idle += TICKS - foreign[i];
            readings.add(new Reading((i + 1) * SECOND, new CpuTimes(total, idle, 0)));
        }
        return ForeignLoad.of(readings);
    }

    private static long[] flat(int n, long each) {
        long[] a = new long[n];
        java.util.Arrays.fill(a, each);
        return a;
    }

    @Test
    void meanIsTheExactWindowFractionNotAnAverageOfIntervals() {
        List<Reading> r = List.of(
                new Reading(0, new CpuTimes(0, 0, 0)),
                new Reading(SECOND, new CpuTimes(100, 0, 0)), // 100 ticks, all foreign
                new Reading(3 * SECOND, new CpuTimes(1_100, 900, 0))); // 1000 ticks, 10 % foreign
        ForeignLoad load = ForeignLoad.of(r);
        assertThat(load.mean()).isCloseTo(200.0 / 1_100, within(1e-12));
        assertThat(load.intervals()).isEqualTo(2);
        assertThat(load.seconds()).isEqualTo(3);
        assertThat(load.peak()).isEqualTo(1);
    }

    @Test
    void subtractsTheProcessOwnTime() {
        ForeignLoad load = ForeignLoad.of(
                List.of(new Reading(0, new CpuTimes(0, 0, 0)), new Reading(SECOND, new CpuTimes(1_000, 400, 200))));
        assertThat(load.mean()).isEqualTo(0.4);
    }

    @Test
    void oneBusyLogicalCpuIsGateValidButProposedInvalid() {
        for (int cpus : new int[] {6, 32}) {
            long oneCpu = TICKS / cpus; // one fully busy CPU out of `cpus`
            Assessment a = seconds(flat(90, oneCpu)).assess(cpus, 0.25, 90);
            assertThat(a.gateInvalid()).as("gate on %d CPUs", cpus).isFalse();
            assertThat(a.proposedValid()).as("proposed on %d CPUs", cpus).isFalse();
            assertThat(a.reasons()).anyMatch(s -> s.startsWith("proposed: mean"));
        }
    }

    @Test
    void quietRunIsValidUnderBoth() {
        Assessment a = seconds(flat(90, 100)).assess(8, 0.25, 90);
        assertThat(a.gateInvalid()).isFalse();
        assertThat(a.proposedValid()).isTrue();
        assertThat(a.reasons()).isEmpty();
    }

    @Test
    void aShortBurstDoesNotMoveP95ButAWiderOneDoes() {
        long[] one = flat(60, 0);
        one[10] = TICKS;
        assertThat(seconds(one).p95()).isZero();
        assertThat(seconds(one).peak()).isEqualTo(1);
        long[] five = flat(60, 0);
        for (int i = 0; i < 5; i++) {
            five[i * 10] = TICKS;
        }
        assertThat(seconds(five).p95()).isEqualTo(1);
    }

    @Test
    void nearestRankPercentile() {
        // ten intervals 0.1 .. 1.0: rank ceil(9.5) = 10 -> the largest
        long[] f = new long[10];
        for (int i = 0; i < 10; i++) {
            f[i] = (i + 1) * TICKS / 10;
        }
        assertThat(seconds(f).p95()).isEqualTo(1.0);
    }

    @Test
    void meanLimitBoundaryIsInclusive() {
        // 5 CPUs: min(0.10, 0.50 / 5) = 0.10
        assertThat(ForeignLoad.proposedMeanLimit(5)).isEqualTo(0.1);
        assertThat(seconds(flat(90, 1_000)).assess(5, 0.25, 90).proposedValid()).isTrue();
        assertThat(seconds(flat(90, 1_001)).assess(5, 0.25, 90).proposedValid()).isFalse();
    }

    @Test
    void burstLimitBoundaryIsInclusive() {
        // 5 CPUs: min(0.20, 1.00 / 5) = 0.20; every interval at 0.20 -> p95 0.20, mean 0.20 is over the mean limit,
        // so check the burst on its own: mean stays at the limit with a quiet majority
        assertThat(ForeignLoad.proposedBurstLimit(5)).isEqualTo(0.2);
        assertThat(ForeignLoad.proposedBurstLimit(32)).isEqualTo(1.0 / 32);
        // 60 intervals: 57 quiet, 3 at 0.2 -> p95 is quiet; 4 at 0.2 -> p95 is 0.2 (exactly the limit, valid on p95)
        long[] four = flat(60, 0);
        for (int i = 0; i < 4; i++) {
            four[i] = 2_000;
        }
        ForeignLoad load = seconds(four);
        assertThat(load.p95()).isEqualTo(0.2);
        assertThat(load.assess(5, 0.25, 90).reasons()).noneMatch(s -> s.contains("p95"));
        long[] over = flat(60, 0);
        for (int i = 0; i < 4; i++) {
            over[i] = 2_001;
        }
        assertThat(seconds(over).assess(5, 0.25, 90).reasons()).anyMatch(s -> s.contains("p95"));
    }

    @Test
    void gateLimitBoundaryIsInclusive() {
        assertThat(seconds(flat(10, 2_500)).assess(1, 0.25, 90).gateInvalid()).isFalse();
        assertThat(seconds(flat(10, 2_501)).assess(1, 0.25, 90).gateInvalid()).isTrue();
    }

    @Test
    void shortPreflightIsProposedInvalidOnly() {
        Assessment a = seconds(flat(90, 0)).assess(8, 0.25, 59.9);
        assertThat(a.gateInvalid()).isFalse();
        assertThat(a.proposedValid()).isFalse();
        assertThat(seconds(flat(90, 0)).assess(8, 0.25, 60).proposedValid()).isTrue();
    }

    @Test
    void unavailableIsInvalidWithItsReasonNeverZero() {
        ForeignLoad load = ForeignLoad.unavailable("no /proc/stat");
        assertThat(load.available()).isFalse();
        assertThat(load.mean()).isEqualTo(1);
        Assessment a = load.assess(8, 0.25, 90);
        assertThat(a.gateInvalid()).isTrue();
        assertThat(a.proposedValid()).isFalse();
        assertThat(a.reasons()).anyMatch(s -> s.contains("no /proc/stat"));
    }

    @Test
    void readingsMillisecondsApartAreMergedNotUnavailable() {
        // a window's own readings land next to the sampler's; the counters have not moved between them
        List<Reading> r = List.of(
                new Reading(0, new CpuTimes(0, 0, 0)),
                new Reading(SECOND / 1000, new CpuTimes(0, 0, 0)),
                new Reading(SECOND, new CpuTimes(100, 90, 0)),
                new Reading(2 * SECOND, new CpuTimes(200, 180, 0)),
                new Reading(2 * SECOND + SECOND / 500, new CpuTimes(200, 180, 0)));
        ForeignLoad load = ForeignLoad.of(r);
        assertThat(load.available()).isTrue();
        assertThat(load.intervals()).isEqualTo(2);
        assertThat(load.mean()).isCloseTo(0.10, within(1e-9));
        assertThat(load.p95()).isCloseTo(0.10, within(1e-9));
        assertThat(load.seconds()).isCloseTo(2.002, within(1e-9));
    }

    @Test
    void tooFewOrInconsistentReadingsAreUnavailableNotAnException() {
        assertThat(ForeignLoad.of(List.of()).available()).isFalse();
        assertThat(ForeignLoad.of(List.of(new Reading(0, new CpuTimes(1, 0, 0))))
                        .available())
                .isFalse();
        ForeignLoad stalled = ForeignLoad.of(
                List.of(new Reading(0, new CpuTimes(100, 50, 0)), new Reading(SECOND, new CpuTimes(100, 50, 0))));
        assertThat(stalled.available()).isFalse();
        assertThat(stalled.assess(8, 0.25, 90).gateInvalid()).isTrue();
        ForeignLoad regress = ForeignLoad.of(
                List.of(new Reading(0, new CpuTimes(100, 50, 0)), new Reading(SECOND, new CpuTimes(200, 40, 0))));
        assertThat(regress.available()).isFalse();
        ForeignLoad backwards = ForeignLoad.of(
                List.of(new Reading(SECOND, new CpuTimes(100, 50, 0)), new Reading(0, new CpuTimes(200, 60, 0))));
        assertThat(backwards.available()).isFalse();
    }

    @Test
    void fixedLoadIsItsOwnMeanP95AndPeak() {
        ForeignLoad load = ForeignLoad.fixed(0.4);
        assertThat(load.mean()).isEqualTo(0.4);
        assertThat(load.p95()).isEqualTo(0.4);
        assertThat(load.peak()).isEqualTo(0.4);
        assertThat(load.assess(8, 0.25, 90).gateInvalid()).isTrue();
    }
}
