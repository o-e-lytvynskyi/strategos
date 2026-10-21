package dev.strategos.core.diag;

import java.util.Arrays;

/** A fixed window of the last N samples. Recording is constant time; the statistics sort a copy (cold path). */
public final class RingHistogram {
    private final long[] samples;
    private int count;
    private int next;
    private long last;

    public RingHistogram(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity " + capacity + " must be at least 1");
        }
        samples = new long[capacity];
    }

    public void record(long value) {
        samples[next] = value;
        next = next + 1 == samples.length ? 0 : next + 1;
        if (count < samples.length) {
            count++;
        }
        last = value;
    }

    /** The samples in the window, at most the capacity. */
    public int count() {
        return count;
    }

    /** The newest sample, or 0 before the first. */
    public long last() {
        return last;
    }

    /** The largest sample in the window, or 0 when it is empty. */
    public long max() {
        long max = count == 0 ? 0 : Long.MIN_VALUE;
        for (int i = 0; i < count; i++) {
            max = Math.max(max, samples[i]);
        }
        return max;
    }

    /** The nearest-rank percentile over the window, {@code q} in [0, 1], or 0 when it is empty. */
    public long percentile(double q) {
        if (!(q >= 0 && q <= 1)) {
            throw new IllegalArgumentException("q " + q + " outside [0, 1]");
        }
        if (count == 0) {
            return 0;
        }
        long[] sorted = Arrays.copyOf(samples, count);
        Arrays.sort(sorted);
        // The small offset keeps a rank like 0.07 * 100 = 7.000000000000001 from rounding up to 8.
        int rank = (int) Math.ceil(q * count - 1e-9);
        return sorted[Math.max(1, Math.min(rank, count)) - 1];
    }
}
