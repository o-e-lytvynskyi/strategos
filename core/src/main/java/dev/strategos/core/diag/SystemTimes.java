package dev.strategos.core.diag;

import dev.strategos.core.port.Clock;

/**
 * PERF-16's times of the territory, garrison and capture systems: what the territory system's {@code run} costs the
 * tick thread, how long of that it waits to join a recompute's job, and the garrison and capture systems' runs, and
 * PERF-17's of the formation system, the ability system and the wild animals' thinking (fauna), read from the core's
 * clock for the soaks' reports. Measured only: never saved, hashed or read by the simulation.
 */
public final class SystemTimes {
    private Clock clock = () -> 0L;
    private long territoryNanos;
    private long joinNanos;
    private long garrisonNanos;
    private long captureNanos;
    private long formationNanos;
    private long abilityNanos;
    private long faunaNanos;

    /**
     * The nanoseconds so far: the territory system's whole run, its job joins, the garrison and capture systems, the
     * formation system, the ability system (cooldowns, buffs and autocast) and the wild animals' thinking.
     */
    public record Stats(
            long territoryNanos,
            long joinNanos,
            long garrisonNanos,
            long captureNanos,
            long formationNanos,
            long abilityNanos,
            long faunaNanos) {}

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

    /** The formation system, run from {@code start}. */
    public void formation(long start) {
        formationNanos += clock.nanoTime() - start;
    }

    /** The ability system, run from {@code start}. */
    public void ability(long start) {
        abilityNanos += clock.nanoTime() - start;
    }

    /** One wild animal's turn to think, from {@code start}. */
    public void fauna(long start) {
        faunaNanos += clock.nanoTime() - start;
    }

    public Stats stats() {
        return new Stats(
                territoryNanos, joinNanos, garrisonNanos, captureNanos, formationNanos, abilityNanos, faunaNanos);
    }
}
