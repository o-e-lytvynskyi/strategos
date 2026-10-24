package dev.strategos.core.sim.econ;

import dev.strategos.core.port.Clock;

/**
 * PERF-07.4's economy times: how often and how long the node index is searched for a gatherer's next node, is built,
 * and a load's dropsite is looked for, read from the core's clock for the soak's report. Measured only: never saved,
 * hashed or read by the simulation.
 */
public final class EconomyTimes {
    private Clock clock = () -> 0L;
    private long searches;
    private long searchNanos;
    private long builds;
    private long buildNanos;
    private long dropsites;
    private long dropsiteNanos;

    /** The counts and nanoseconds so far. */
    public record Stats(
            long searches, long searchNanos, long builds, long buildNanos, long dropsites, long dropsiteNanos) {}

    /** The clock the times are read from; until it is set they read 0. */
    public void clock(Clock clock) {
        this.clock = clock;
    }

    public long start() {
        return clock.nanoTime();
    }

    /** A node index search that began at {@code start}. */
    public void searched(long start) {
        searches++;
        searchNanos += clock.nanoTime() - start;
    }

    /** A node index build that began at {@code start}. */
    public void built(long start) {
        builds++;
        buildNanos += clock.nanoTime() - start;
    }

    /** A dropsite query that began at {@code start}. */
    public void dropsite(long start) {
        dropsites++;
        dropsiteNanos += clock.nanoTime() - start;
    }

    public Stats stats() {
        return new Stats(searches, searchNanos, builds, buildNanos, dropsites, dropsiteNanos);
    }
}
