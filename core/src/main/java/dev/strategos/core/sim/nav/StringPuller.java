package dev.strategos.core.sim.nav;

import dev.strategos.core.shared.HotPath;
import java.util.Arrays;

/**
 * Turns a search's node path into waypoints (spec §5.6). From each anchor the next waypoint is the farthest later node
 * the anchor has line of walk to: the run of visible nodes is probed at doubling distances and the first failure is
 * narrowed down by bisection, so a long straight stretch costs a few walks instead of one per node. Waypoints are
 * column centres; when the path reaches the goal, the last one is the goal point itself, kept apart from the goal
 * column's centre only when the waypoint before cannot see it. At most {@link PathStore.Waypoints#MAX}; a longer path
 * keeps its first ones and is marked truncated.
 */
@HotPath
public final class StringPuller {
    private StringPuller() {}

    /**
     * The waypoints of {@code nodes}, a path from its first node to its last, which become anchors in turn. With
     * {@code toGoal} the path ends in the column of ({@code goalX}, {@code goalZ}), where the unit should stop exactly.
     */
    public static PathStore.Waypoints pull(SurfaceGrid grid, int[] nodes, boolean toGoal, double goalX, double goalZ) {
        return pull(grid, nodes, toGoal, goalX, goalZ, Double.NaN, Double.NaN);
    }

    /**
     * As {@link #pull(SurfaceGrid, int[], boolean, double, double)}, the first anchor at ({@code fromX}, {@code
     * fromZ}) in the first node's column, where the unit stood (NaN for the column's centre): the first waypoint is
     * then in line of walk from that point. Where that point sees no node but its own, the column's centre comes
     * first, and the pulling goes on from there.
     */
    public static PathStore.Waypoints pull(
            SurfaceGrid grid, int[] nodes, boolean toGoal, double goalX, double goalZ, double fromX, double fromZ) {
        int max = PathStore.Waypoints.MAX;
        double[] xs = new double[max];
        double[] zs = new double[max];
        int[] floors = new int[max];
        int[] from = new int[max]; // the anchor node each waypoint is seen from
        double[] seenX = new double[max]; // and the point it is seen from
        double[] seenZ = new double[max];
        int count = 0;
        int last = nodes.length - 1;
        int anchor = 0;
        boolean exact = !Double.isNaN(fromX) && last >= 0;
        if (exact && last > 0 && farthestFrom(grid, nodes, fromX, fromZ) == 0) {
            int n = nodes[0];
            xs[0] = grid.nodeX(n) + 0.5; // round the corner of its own column first
            zs[0] = grid.nodeZ(n) + 0.5;
            floors[0] = floor(grid, n);
            from[0] = n;
            seenX[0] = fromX;
            seenZ[0] = fromZ;
            count = 1;
            exact = false;
        }
        boolean truncated = false;
        while (anchor < last) {
            if (count == max) {
                truncated = true;
                break;
            }
            boolean fromStart = anchor == 0 && exact;
            int next = fromStart ? farthestFrom(grid, nodes, fromX, fromZ) : farthest(grid, nodes, anchor);
            int n = nodes[next];
            xs[count] = grid.nodeX(n) + 0.5;
            zs[count] = grid.nodeZ(n) + 0.5;
            floors[count] = floor(grid, n);
            from[count] = nodes[anchor];
            seenX[count] = fromStart ? fromX : grid.nodeX(nodes[anchor]) + 0.5;
            seenZ[count] = fromStart ? fromZ : grid.nodeZ(nodes[anchor]) + 0.5;
            count++;
            anchor = next;
        }
        if (toGoal && !truncated && last >= 0) {
            int goal = nodes[last];
            boolean sees = count > 0
                    && LineOfWalk.walk(grid, from[count - 1], seenX[count - 1], seenZ[count - 1], goalX, goalZ) == goal;
            if (sees) {
                xs[count - 1] = goalX; // the goal point replaces its column's centre
                zs[count - 1] = goalZ;
            } else if (count < max) {
                xs[count] = goalX;
                zs[count] = goalZ;
                floors[count] = floor(grid, goal);
                count++;
            } else {
                truncated = true;
            }
        }
        return new PathStore.Waypoints(
                Arrays.copyOf(xs, count), Arrays.copyOf(zs, count), Arrays.copyOf(floors, count), truncated);
    }

    /** The farthest index the point (x, z) in the first node's column sees; 0 when it sees none but its own. */
    private static int farthestFrom(SurfaceGrid grid, int[] nodes, double x, double z) {
        int last = nodes.length - 1;
        int seen = 0;
        int unseen = -1;
        for (int reach = 1; seen < last; reach *= 2) {
            int probe = Math.min(reach, last);
            if (sees(grid, nodes[0], x, z, nodes[probe])) {
                seen = probe;
            } else {
                unseen = probe;
                break;
            }
        }
        while (unseen > seen + 1) {
            int mid = (seen + unseen) >>> 1;
            if (sees(grid, nodes[0], x, z, nodes[mid])) {
                seen = mid;
            } else {
                unseen = mid;
            }
        }
        return seen;
    }

    private static boolean sees(SurfaceGrid grid, int from, double x, double z, int to) {
        return LineOfWalk.walk(grid, from, x, z, grid.nodeX(to) + 0.5, grid.nodeZ(to) + 0.5) == to;
    }

    /** The farthest index after {@code anchor} that the anchor sees, by doubling probes and then bisection. */
    private static int farthest(SurfaceGrid grid, int[] nodes, int anchor) {
        int last = nodes.length - 1;
        int seen = anchor + 1; // neighbours on a path are linked, so the next node is always in sight
        int unseen = -1;
        for (int reach = 2; seen < last; reach *= 2) {
            int probe = Math.min(anchor + reach, last);
            if (LineOfWalk.between(grid, nodes[anchor], nodes[probe])) {
                seen = probe;
            } else {
                unseen = probe;
                break;
            }
        }
        while (unseen > seen + 1) {
            int mid = (seen + unseen) >>> 1;
            if (LineOfWalk.between(grid, nodes[anchor], nodes[mid])) {
                seen = mid;
            } else {
                unseen = mid;
            }
        }
        return seen;
    }

    private static int floor(SurfaceGrid grid, int node) {
        return Surface.floor(grid.surface(grid.nodeX(node), grid.nodeZ(node), SurfaceGrid.nodeLayer(node)));
    }
}
