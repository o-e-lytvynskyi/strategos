package dev.strategos.core.testkit;

import dev.strategos.core.port.MetricsSink;

/** A metrics sink that throws at the end of one tick: a fault inside the step without test code in core. */
public final class ThrowingMetrics implements MetricsSink {
    private final long failAtTick;

    public ThrowingMetrics(long failAtTick) {
        this.failAtTick = failAtTick;
    }

    @Override
    public void onPhase(long tick, int phaseOrdinal) {}

    @Override
    public void onTick(long tick, long[] phaseNanos) {
        if (tick == failAtTick) {
            throw new IllegalStateException("injected fault at tick " + tick);
        }
    }
}
