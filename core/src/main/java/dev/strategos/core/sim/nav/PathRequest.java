package dev.strategos.core.sim.nav;

import dev.strategos.core.shared.CoreApi;

/**
 * One search a unit waits for (04 §9.3): its id, the unit, the tick it was made, the surface it starts on and the
 * point it stood at (match-local; NaN for a request made from a column's centre), the goal on the ground plane, the
 * expansion cap, and the group fields PATH-09 uses (0 and false until then).
 */
@CoreApi
public record PathRequest(
        int id,
        int handle,
        long tick,
        int fromNode,
        double fromX,
        double fromZ,
        double goalX,
        double goalZ,
        int cap,
        int groupId,
        boolean join) {
    /** A request from the centre of {@code fromNode}'s column. */
    public PathRequest(
            int id,
            int handle,
            long tick,
            int fromNode,
            double goalX,
            double goalZ,
            int cap,
            int groupId,
            boolean join) {
        this(id, handle, tick, fromNode, Double.NaN, Double.NaN, goalX, goalZ, cap, groupId, join);
    }
}
