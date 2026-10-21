package dev.strategos.core.sim;

/** How often a system runs: on ticks where {@code floorMod(tick - offset, period) == 0}. */
public record Cadence(int period, int offset) {
    public static final Cadence EVERY_TICK = new Cadence(1, 0);

    public Cadence {
        if (period < 1 || offset < 0 || offset >= period) {
            throw new IllegalArgumentException(
                    "cadence needs period >= 1 and 0 <= offset < period: " + period + ", " + offset);
        }
    }

    public boolean runsAt(long tick) {
        return Math.floorMod(tick - offset, period) == 0;
    }
}
