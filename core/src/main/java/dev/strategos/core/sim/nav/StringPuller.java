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
        int max = PathStore.Waypoints.MAX;
        double[] xs = new double[max];
        double[] zs = new double[max];
        int[] floors = new int[max];
        int[] from = new int[max]; // the anchor node each waypoint is seen from
        int count = 0;
        int last = nodes.length - 1;
        int anchor = 0;
        boolean truncated = false;
        while (anchor < last) {
            if (count == max) {
                truncated = true;
                break;
            }
            int next = farthest(grid, nodes, anchor);
            int n = nodes[next];
            xs[count] = grid.nodeX(n) + 0.5;
            zs[count] = grid.nodeZ(n) + 0.5;
            floors[count] = floor(grid, n);
            from[count] = nodes[anchor];
            count++;
            anchor = next;
        }
        if (toGoal && !truncated && last >= 0) {
            int goal = nodes[last];
            int seer = count > 0 ? from[count - 1] : goal;
            boolean sees = count > 0
                    && LineOfWalk.walk(grid, seer, grid.nodeX(seer) + 0.5, grid.nodeZ(seer) + 0.5, goalX, goalZ)
                            == goal;
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
