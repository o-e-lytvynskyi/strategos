package dev.strategos.core.sim.econ;

import dev.strategos.core.shared.CoreApi;

/**
 * Resource amounts in the core (ECO-01): a {@code long} in thousandths of a unit, so gathering rates, carried loads and
 * node remainders add up exactly in any order. Templates give whole or decimal units; {@link #of} rounds them half away
 * from zero once, when a set is read.
 */
@CoreApi
public final class Amount {
    /** Thousandths in one unit. */
    public static final long UNIT = 1_000;

    private Amount() {}

    /** {@code units} in thousandths; a value that is no finite number is 0. */
    public static long of(double units) {
        return Double.isFinite(units) ? Math.round(units * UNIT) : 0;
    }

    /** Whole units, rounded down: what a player sees in the resource bar. */
    public static long whole(long thousandths) {
        return Math.floorDiv(thousandths, UNIT);
    }

    /** The amount as a player reads it: whole units, or with up to three decimals when not whole. */
    public static String format(long thousandths) {
        long whole = Math.floorDiv(thousandths, UNIT);
        long rest = Math.floorMod(thousandths, UNIT);
        if (rest == 0) {
            return Long.toString(whole);
        }
        String decimals = String.format(java.util.Locale.ROOT, "%03d", rest).replaceAll("0+$", "");
        return whole + "." + decimals;
    }
}
