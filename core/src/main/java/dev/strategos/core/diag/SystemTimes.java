package dev.strategos.core.diag;

import dev.strategos.core.port.Clock;

/**
 * PERF-16's times of the territory, garrison and capture systems: what the territory system's {@code run} costs the
 * tick thread, how long of that it waits to join a recompute's job, and the garrison and capture systems' runs, read
 * from the core's clock for the territory soak's report. Measured only: never saved, hashed or read by the simulation.
 */
public final class SystemTimes {
    private Clock clock = () -> 0L;
    private long territoryNanos;
    private long joinNanos;
    private long garrisonNanos;
    private long captureNanos;

    /** The nanoseconds so far: the territory system's whole run, its job joins, the garrison and capture systems. */
    public record Stats(long territoryNanos, long joinNanos, long garrisonNanos, long captureNanos) {}

    /** The clock the times are read from; until it is set they read 0. */
    public void clock(Clock clock) {
        this.clock = clock;
    }

    public long start() {
        return clock.nanoTime();
    }

    /** The territory system, run from {@code start}. */
    public void territory(long start) {
        territoryNanos += clock.nanoTime() - start;
    }

    /** A recompute's job joined, waited on from {@code start}. */
    public void joined(long start) {
        joinNanos += clock.nanoTime() - start;
    }

    /** The garrison system, run from {@code start}. */
    public void garrison(long start) {
        garrisonNanos += clock.nanoTime() - start;
    }

    /** The capture system, run from {@code start}. */
    public void capture(long start) {
        captureNanos += clock.nanoTime() - start;
    }

    public Stats stats() {
        return new Stats(territoryNanos, joinNanos, garrisonNanos, captureNanos);
    }
}
