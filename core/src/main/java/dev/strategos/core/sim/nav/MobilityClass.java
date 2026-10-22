package dev.strategos.core.sim.nav;

import dev.strategos.core.shared.CoreApi;

/** How a kind of unit moves over surfaces, in 1/16 block; M1 has infantry only (PATH-11 adds the rest). */
@CoreApi
public enum MobilityClass {
    /** Clearance 1.8125 (a humanoid is 1.8 tall), steps up to 1 block, drops up to 3. */
    INFANTRY(29, 16, 48);

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
