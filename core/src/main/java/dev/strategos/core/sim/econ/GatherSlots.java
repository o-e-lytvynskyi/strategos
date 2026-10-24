package dev.strategos.core.sim.econ;

import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.nav.Surface;
import dev.strategos.core.sim.nav.SurfaceGrid;
import java.util.function.IntPredicate;

/**
 * Which of a node's work slots a unit can stand on (ECO-05): the gatherer's test when it claims one, and the free
 * slots a computer player is shown, so it never sends a worker to room that is not there (AI-02).
 */
public final class GatherSlots {
    /** How far from its node's height a slot's floor may be, in blocks. */
    public static final double RISE = 2;

    private GatherSlots() {}

    /**
     * A slot cell a unit can stand on: in the match, with a passable floor within {@value #RISE} blocks of the node's
     * base, and no building there. A match without terrain stands everywhere.
     */
    public static IntPredicate standable(MatchState s, int node) {
        SurfaceGrid grid = s.surfaces();
        if (grid.version() == 0) {
            return cell -> true;
        }
        double base = s.nodes().baseY(node);
        int minY = s.config().bounds().minY();
        return cell -> {
            int x = cell >>> 16;
            int z = cell & 0xFFFF;
            if (!grid.inside(x, z) || s.footprintAt(x, z)) {
                return false;
            }
            for (int layer = 0; layer < grid.count(x, z); layer++) {
                int surface = grid.surface(x, z, layer);
                if (Surface.passable(surface) && Math.abs(minY + Surface.floor(surface) / 16.0 - base) <= RISE) {
                    return true;
                }
            }
            return false;
        };
    }
}
