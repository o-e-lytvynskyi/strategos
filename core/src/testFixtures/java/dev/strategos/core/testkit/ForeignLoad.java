package dev.strategos.core.testkit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Foreign (not this process) CPU load over a run of timestamped cumulative readings: the exact window mean, and the
 * per-interval fractions that show bursts a mean hides. Missing or inconsistent counters make the whole measurement
 * {@linkplain #unavailable unavailable}, never zero. The proposed limits are report-only until the policy is adopted.
 */
public final class ForeignLoad {
    /** One reading and the {@link System#nanoTime()}-style instant it was taken. */
    public record Reading(long nanos, CpuTimes times) {}

    /** What a measurement says about a run: the current gate, and the proposed policy (reported, not enforced). */
    public record Assessment(boolean gateInvalid, boolean proposedValid, List<String> reasons) {
        public Assessment {
            reasons = List.copyOf(reasons);
        }
    }

    /**
     * The shortest interval kept: a reading closer than this to the one before is merged into the next interval. The
     * Mach counters move in 100 Hz ticks while readings may be milliseconds apart (a window's own reading next to the
     * sampler's): such an interval has no ticks at all, or a meaningless fraction (calibration round 3).
     */
    public static final long MIN_INTERVAL_NANOS = 500_000_000L;

    /** Preflight the proposed policy asks for, in seconds. */
    public static final double PROPOSED_PREFLIGHT_SECONDS = 60;

    private final @Nullable String unavailableReason;
    private final double mean;
    private final double[] intervals;
    private final double seconds;

    private ForeignLoad(@Nullable String unavailableReason, double mean, double[] intervals, double seconds) {
        this.unavailableReason = unavailableReason;
        this.mean = mean;
        this.intervals = intervals;
        this.seconds = seconds;
    }

    public static ForeignLoad unavailable(String reason) {
        return new ForeignLoad(reason, 1, new double[0], 0);
    }

    /** A stated load, for the self-test override: one interval at {@code fraction}, clamped to [0, 1]. */
    public static ForeignLoad fixed(double fraction) {
        double f = Math.max(0, Math.min(1, fraction));
        return new ForeignLoad(null, f, new double[] {f}, 0);
    }

    /**
     * Readings in time order, intervals under {@link #MIN_INTERVAL_NANOS} merged into the next (the last into the one
     * before); fewer than two, or counters that do not advance consistently, are unavailable.
     */
    public static ForeignLoad of(List<Reading> readings) {
        if (readings.size() < 2) {
            return unavailable("fewer than two CPU readings (" + readings.size() + ")");
        }
        for (int i = 1; i < readings.size(); i++) {
            if (readings.get(i).nanos() <= readings.get(i - 1).nanos()) {
                return unavailable("CPU readings are not in time order");
            }
        }
        List<Reading> kept = new ArrayList<>();
        kept.add(readings.get(0));
        for (int i = 1; i < readings.size(); i++) {
            Reading r = readings.get(i);
            boolean last = i == readings.size() - 1;
            if (r.nanos() - kept.get(kept.size() - 1).nanos() >= MIN_INTERVAL_NANOS) {
                kept.add(r);
            } else if (last) {
                if (kept.size() > 1) {
                    kept.set(kept.size() - 1, r); // the short tail joins the interval before it
                } else {
                    kept.add(r); // the whole run is shorter than one interval
                }
            }
        }
        readings = kept;
        double[] fractions = new double[readings.size() - 1];
        try {
            for (int i = 0; i < fractions.length; i++) {
                fractions[i] =
                        readings.get(i).times().foreignUntil(readings.get(i + 1).times());
            }
            Reading first = readings.get(0);
            Reading last = readings.get(readings.size() - 1);
            return new ForeignLoad(
                    null, first.times().foreignUntil(last.times()), fractions, (last.nanos() - first.nanos()) / 1e9);
        } catch (IllegalStateException e) {
            return unavailable(e.getMessage() == null ? "CPU counters inconsistent" : e.getMessage());
        }
    }

    public boolean available() {
        return unavailableReason == null;
    }

    /** Why there is no measurement, or an empty string. */
    public String unavailableReason() {
        return unavailableReason == null ? "" : unavailableReason;
    }

    /** Exact foreign fraction from the first to the last reading (not an average of intervals); 1 if unavailable. */
    public double mean() {
        return mean;
    }

    /** Nearest-rank 95th percentile of the intervals: the value at rank ceil(0.95 n) of the sorted fractions. */
    public double p95() {
        if (intervals.length == 0) {
            return available() ? 0 : 1;
        }
        double[] sorted = intervals.clone();
        Arrays.sort(sorted);
        int rank = (95 * sorted.length + 99) / 100;
        return sorted[rank - 1];
    }

    public double peak() {
        if (intervals.length == 0) {
            return available() ? 0 : 1;
        }
        return Arrays.stream(intervals).max().orElse(0);
    }

    public int intervals() {
        return intervals.length;
    }

    /** Time from the first to the last reading, in seconds. */
    public double seconds() {
        return seconds;
    }

    /** Proposed limit on the mean: at most 10 % of the machine, and at most half a logical CPU. */
    public static double proposedMeanLimit(int cpus) {
        return Math.min(0.10, 0.50 / cpus);
    }

    /** Proposed limit on the p95 of one-second intervals: at most 20 %, and at most one logical CPU. */
    public static double proposedBurstLimit(int cpus) {
        return Math.min(0.20, 1.00 / cpus);
    }

    /** Reasons are prefixed {@code gate:} (the current verdict) or {@code proposed:} (report-only). */
    public Assessment assess(int cpus, double gateMeanLimit, double preflightSeconds) {
        List<String> reasons = new ArrayList<>();
        if (!available()) {
            reasons.add("gate: foreign CPU load unavailable: " + unavailableReason);
            reasons.add("proposed: foreign CPU load unavailable");
            return new Assessment(true, false, reasons);
        }
        boolean gateInvalid = mean > gateMeanLimit;
        if (gateInvalid) {
            reasons.add("gate: mean foreign load " + pct(mean) + " above " + pct(gateMeanLimit));
        }
        boolean valid = true;
        if (mean > proposedMeanLimit(cpus)) {
            valid = false;
            reasons.add("proposed: mean foreign load " + pct(mean) + " above " + pct(proposedMeanLimit(cpus)) + " on "
                    + cpus + " CPUs");
        }
        if (p95() > proposedBurstLimit(cpus)) {
            valid = false;
            reasons.add("proposed: p95 foreign load " + pct(p95()) + " above " + pct(proposedBurstLimit(cpus)) + " on "
                    + cpus + " CPUs");
        }
        if (preflightSeconds < PROPOSED_PREFLIGHT_SECONDS) {
            valid = false;
            reasons.add("proposed: preflight " + Math.round(preflightSeconds) + " s, under "
                    + Math.round(PROPOSED_PREFLIGHT_SECONDS) + " s");
        }
        return new Assessment(gateInvalid, valid, reasons);
    }

    private static String pct(double fraction) {
        return String.format(java.util.Locale.ROOT, "%.2f%%", fraction * 100);
    }
}
