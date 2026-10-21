package dev.strategos.core.port;

/** The no-op {@link MetricsSink} behind {@link MetricsSink#none()}. */
enum NoMetrics implements MetricsSink {
    INSTANCE;

    @Override
    public void onPhase(long tick, int phaseOrdinal) {}

    @Override
    public void onTick(long tick, long[] phaseNanos) {}
}
