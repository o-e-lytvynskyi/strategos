package dev.strategos.core.sim.nav;

import dev.strategos.core.port.JobScratch;
import dev.strategos.core.port.PureJob;
import dev.strategos.core.shared.DetMath;
import java.util.Arrays;
import java.util.BitSet;

/**
 * One request's search (04 §9.3): the goal made reachable, A* over the grid as it was when the request was made, then
 * string pulling. Pure: the grid is immutable (copy-on-write), and the search's scratch belongs to the thread that runs
 * it.
 */
public record PathJob(SurfaceGrid grid, PathRequest request) implements PureJob<PathResult> {
    /** A goal point, its column, and the columns inspected to find it (counted as expansions). */
    record Goal(double x, double z, int columnX, int columnZ, int inspected) {}

    @Override
    public PathResult run(JobScratch scratch) {
        GridAStar search = scratch.get(GridAStar.class, GridAStar::new);
        Goal goal = makeGoalReachable(grid, grid.reachableFrom(request.fromNode()), request.goalX(), request.goalZ());
        GridAStar.Result found = search.search(grid, request.fromNode(), goal.columnX(), goal.columnZ(), request.cap());
        PathStore.Waypoints waypoints =
                StringPuller.pull(grid, found.nodes(), found.status() == PathStatus.OK, goal.x(), goal.z());
        return new PathResult(request.id(), found.status(), waypoints, found.expansions() + goal.inspected());
    }

    /**
     * PATH-04: the goal itself when a surface of its column is in one of the {@code reachable} components; otherwise
     * the centre of the nearest column that has one. Pages are searched in rings around the goal's page, those holding the component
     * only; in each, the columns by distance to the goal (squared, in millionths of a block), skipping by the
     * component's column mask, to the first that holds it, which counts as an inspected column. Rings stop once no
     * page of the next can hold a nearer column. Ties go to the page, then the column, met first.
     */
    static Goal makeGoalReachable(SurfaceGrid grid, BitSet reachable, double gx, double gz) {
        int cx = PathService.clampColumn(gx, grid.sizeX());
        int cz = PathService.clampColumn(gz, grid.sizeZ());
        if (holds(grid, cx, cz, reachable)) {
            return new Goal(gx, gz, cx, cz, 0);
        }
        int gpx = cx >> 4;
        int gpz = cz >> 4;
        int inspected = 0;
        int bestX = -1;
        int bestZ = -1;
        double bestDistance = Double.POSITIVE_INFINITY;
        long[] order = new long[SurfacePage.COLUMNS];
        int rings = Math.max(grid.pagesX(), grid.pagesZ());
        for (int r = 0; r <= rings; r++) {
            if ((r - 1) * (double) VoxelSlice.SIDE > bestDistance) {
                break;
            }
            for (int pz = gpz - r; pz <= gpz + r; pz++) {
                for (int px = gpx - r; px <= gpx + r; px++) {
                    if (Math.max(Math.abs(px - gpx), Math.abs(pz - gpz)) != r
                            || px < 0
                            || pz < 0
                            || px >= grid.pagesX()
                            || pz >= grid.pagesZ()
                            || !grid.components().pageHas(pz * grid.pagesX() + px, reachable)) {
                        continue;
                    }
                    int page = pz * grid.pagesX() + px;
                    for (int column = 0; column < SurfacePage.COLUMNS; column++) {
                        double dx = px * 16 + column / 16 + 0.5 - gx;
                        double dz = pz * 16 + column % 16 + 0.5 - gz;
                        long squared = Math.round((dx * dx + dz * dz) * (1 << 20)); // to 1e-6 block², < 2^44
                        order[column] = squared << 8 | column;
                    }
                    Arrays.sort(order);
                    for (long key : order) {
                        int column = (int) (key & 0xFF);
                        if (!grid.components().columnHas(page, column, reachable)) {
                            continue; // the page's column mask answers without a look at the column
                        }
                        int x = px * 16 + column / 16;
                        int z = pz * 16 + column % 16;
                        double distance = DetMath.length(x + 0.5 - gx, z + 0.5 - gz);
                        inspected++;
                        if (distance < bestDistance) {
                            bestX = x;
                            bestZ = z;
                            bestDistance = distance;
                        }
                        break; // the page's nearest holding column
                    }
                }
            }
        }
        if (bestX < 0) {
            return new Goal(gx, gz, cx, cz, inspected); // nothing holds it: the search ends as near as it gets
        }
        return new Goal(bestX + 0.5, bestZ + 0.5, bestX, bestZ, inspected);
    }

    private static boolean holds(SurfaceGrid grid, int x, int z, BitSet reachable) {
        int count = grid.count(x, z);
        for (int layer = 0; layer < count; layer++) {
            if (reachable.get(grid.component(grid.node(x, z, layer)))) {
                return true;
            }
        }
        return false;
    }
}
