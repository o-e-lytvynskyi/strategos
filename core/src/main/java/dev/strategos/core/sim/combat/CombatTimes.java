package dev.strategos.core.sim.combat;

import dev.strategos.core.port.Clock;

/**
 * PERF-08's combat times: how often and how long the units look for an enemy to take up (targeting), the centres'
 * volleys are decided, the shots are released and landed, and the damage packets apply, read from the core's clock for
 * the battle soak's report. Measured only: never saved, hashed or read by the simulation.
 */
public final class CombatTimes {
    private Clock clock = () -> 0L;
    private long targetings;
    private long targetingNanos;
    private long volleyNanos;
    private long shotNanos;
    private long damageNanos;

    /** The counts and nanoseconds so far: unit enemy searches, then the three systems' time. */
    public record Stats(long targetings, long targetingNanos, long volleyNanos, long shotNanos, long damageNanos) {}

    /** The clock the times are read from; until it is set they read 0. */
    public void clock(Clock clock) {
        this.clock = clock;
    }

    public long start() {
        return clock.nanoTime();
    }

    /** An enemy search that began at {@code start}. */
    public void targeted(long start) {
        targetings++;
        targetingNanos += clock.nanoTime() - start;
    }

    /** The centres' arrows system, run from {@code start}. */
    public void volleys(long start) {
        volleyNanos += clock.nanoTime() - start;
    }

    /** The shot system, run from {@code start}. */
    public void shots(long start) {
        shotNanos += clock.nanoTime() - start;
    }

    /** The damage system, run from {@code start}. */
    public void damage(long start) {
        damageNanos += clock.nanoTime() - start;
    }

    public Stats stats() {
        return new Stats(targetings, targetingNanos, volleyNanos, shotNanos, damageNanos);
    }
}
