package dev.strategos.core.sim.nav;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.sim.Allies;
import java.util.BitSet;

/** Reachability for one faction: the grid's components, and across the gates its allies own (PATH-12). */
@CoreApi
public final class Reach {
    private Reach() {}

    /**
     * Whether a unit of {@code faction} in component {@code from} reaches component {@code to}: by its own component
     * and the drops downstream of it, or across gates whose owner is allied with {@code faction}.
     */
    public static boolean reachable(SurfaceGrid grid, GateOverlay gates, Allies allies, int faction, int from, int to) {
        return reachableFrom(grid, gates, allies, faction, from).get(to);
    }

    /** Every component a unit of {@code faction} in component {@code from} reaches. */
    public static BitSet reachableFrom(SurfaceGrid grid, GateOverlay gates, Allies allies, int faction, int from) {
        BitSet reach = grid.components().reachable(from);
        GateLinks.Pass pass = gates.passFor(grid, faction, allies);
        if (pass != null) {
            pass.extend(grid.components(), reach);
        }
        return reach;
    }
}
