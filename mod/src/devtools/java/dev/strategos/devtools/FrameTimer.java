package dev.strategos.devtools;

import java.util.Arrays;

/** Durations between a start and an end call, in nanoseconds, up to a fixed count. */
final class FrameTimer {
    private final long[] samples;
    private int count;
    private long start = -1;

    FrameTimer(int capacity) {
        samples = new long[capacity];
    }

    void start() {
        start = System.nanoTime();
    }

    void end() {
        if (start >= 0 && count < samples.length) {
            samples[count++] = System.nanoTime() - start;
        }
        start = -1;
    }

    /** A duration measured elsewhere, in nanoseconds. */
    void add(long nanos) {
        if (count < samples.length) {
            samples[count++] = nanos;
        }
    }

    boolean full() {
        return count == samples.length;
    }

    /** The {@code q} quantile in milliseconds. */
    double ms(double q) {
        long[] sorted = Arrays.copyOf(samples, count);
        Arrays.sort(sorted);
        return sorted.length == 0
                ? Double.NaN
                : sorted[(int) Math.min(sorted.length - 1, Math.floor(q * sorted.length))] / 1e6;
    }

    double maxMs() {
        return Arrays.stream(samples, 0, count).max().orElse(0) / 1e6;
    }
}
