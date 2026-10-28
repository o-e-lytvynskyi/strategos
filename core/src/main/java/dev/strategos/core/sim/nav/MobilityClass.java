package dev.strategos.core.sim.nav;

import dev.strategos.core.shared.CoreApi;

/** How a kind of unit moves over surfaces, in 1/16 block (PATH-11). */
@CoreApi
public enum MobilityClass {
    /** Clearance 1.8125 (a humanoid is 1.8 tall), steps up to 1 block, drops up to 3. */
    INFANTRY(29, 16, 48),
    /** Horses and siege: 2.5 blocks of headroom, with room in the adjacent columns for a radius of 0.7. */
    LARGE(40, 16, 48);

    public static MobilityClass of(String name) {
        return switch (name) {
            case "infantry" -> INFANTRY;
            case "large" -> LARGE;
            default -> throw new IllegalArgumentException("unknown mobility '" + name + "'");
        };
    }

    /** Stable codes for deterministic group keys; never use enum ordinals in state. */
    public int code() {
        return this == INFANTRY ? 0 : 1;
    }

    private final int clearance;
    private final int step;
    private final int drop;

    MobilityClass(int clearance, int step, int drop) {
        this.clearance = clearance;
        this.step = step;
        this.drop = drop;
    }

    public int clearance() {
        return clearance;
    }

    public int step() {
        return step;
    }

    public int drop() {
        return drop;
    }
}
