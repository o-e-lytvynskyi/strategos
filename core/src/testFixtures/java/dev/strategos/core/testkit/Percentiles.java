package dev.strategos.core.testkit;

import java.util.Arrays;

/** Nearest-rank percentiles, as {@code RingHistogram} computes them. */
public final class Percentiles {
    private Percentiles() {}

    /** The nearest-rank percentile of {@code samples}, {@code q} in [0, 1]; no samples throw. */
    public static long percentile(long[] samples, double q) {
        if (!(q >= 0 && q <= 1)) {
            throw new IllegalArgumentException("q " + q + " outside [0, 1]");
        }
        if (samples.length == 0) {
            throw new IllegalArgumentException("no samples");
        }
        long[] sorted = samples.clone();
        Arrays.sort(sorted);
        // The small offset keeps a rank like 0.07 * 100 = 7.000000000000001 from rounding up to 8.
        int rank = (int) Math.ceil(q * sorted.length - 1e-9);
        return sorted[Math.max(1, Math.min(rank, sorted.length)) - 1];
    }
}
