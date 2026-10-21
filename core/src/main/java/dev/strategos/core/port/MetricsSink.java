package dev.strategos.core.port;

/**
 * Per-tick metrics out of {@code MatchCore.step}. A sink that throws is the fault-injection seam: it raises an exception
 * inside the step without any test-only code in core.
 */
public interface MetricsSink {
    /** At the start of every phase, in order. */
    void onPhase(long tick, int phaseOrdinal);

    /** Once, at the end of the last phase; the array is reused, so copy it to keep it. */
    void onTick(long tick, long[] phaseNanos);

    /** A stateless no-op. */
    static MetricsSink none() {
        return NoMetrics.INSTANCE;
    }
}
