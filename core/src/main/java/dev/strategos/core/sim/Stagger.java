package dev.strategos.core.sim;

/** Spreads per-entity work over ticks: each index is selected once every {@code period} ticks. */
public final class Stagger {
    private Stagger() {}

    public static boolean selects(long tick, int index, int period) {
        return Math.floorMod(tick + index, period) == 0;
    }
}
