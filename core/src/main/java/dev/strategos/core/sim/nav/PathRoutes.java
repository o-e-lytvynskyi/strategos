package dev.strategos.core.sim.nav;

import dev.strategos.core.sim.MatchState;

/** The checks of a delivered path against the unit and the grid: where it starts, whether it can be walked, crossed pages. */
final class PathRoutes {
    private PathRoutes() {}

    /**
     * The waypoints a unit follows from where it now stands: a path searched from where it stood when it asked, which
     * provisional steering may have left, gets that point first when the unit cannot walk straight to its first
     * waypoint.
     */
    static PathStore.Waypoints startFrom(
            MatchState s, int i, int fromNode, double fromX, double fromZ, PathStore.Waypoints w) {
        if (w.count() == 0 || Double.isNaN(fromX)) {
            return w;
        }
        SurfaceGrid grid = s.surfacesFor(i);
        double x = s.motion().x(i);
        double z = s.motion().z(i);
        int node = PathService.startNode(
                grid, x, s.motion().y(i), z, s.config().bounds().minY());
        if (node >= 0 && LineOfWalk.walk(grid, node, x, z, w.x(0), w.z(0)) >= 0) {
            return w;
        }
        if (SurfaceGrid.nodeLayer(fromNode) >= grid.count(grid.nodeX(fromNode), grid.nodeZ(fromNode))) {
            return w; // QA-12: the ground it asked from has been built over since: the path stands as found
        }
        int keep = Math.min(w.count(), PathStore.Waypoints.MAX - 1);
        double[] xs = new double[keep + 1];
        double[] zs = new double[keep + 1];
        int[] floors = new int[keep + 1];
        xs[0] = fromX;
        zs[0] = fromZ;
        floors[0] = Surface.floor(
                grid.surface(grid.nodeX(fromNode), grid.nodeZ(fromNode), SurfaceGrid.nodeLayer(fromNode)));
        for (int k = 0; k < keep; k++) {
            xs[k + 1] = w.x(k);
            zs[k + 1] = w.z(k);
            floors[k + 1] = w.floor(k);
        }
        return new PathStore.Waypoints(xs, zs, floors, w.truncated() || keep < w.count());
    }

    /** Whether unit {@code i}'s path can still be walked on the current grid, from where it stands. */
    static boolean walkable(MatchState s, int i) {
        SurfaceGrid grid = s.surfacesFor(i);
        PathStore.Waypoints w = s.paths().path(i);
        double x = s.motion().x(i);
        double z = s.motion().z(i);
        int node = PathService.startNode(
                grid, x, s.motion().y(i), z, s.config().bounds().minY());
        for (int k = 0; k < w.count() && node >= 0; k++) {
            node = LineOfWalk.walk(grid, node, x, z, w.x(k), w.z(k));
            x = w.x(k);
            z = w.z(k);
        }
        return node >= 0;
    }

    /** Whether unit {@code i}'s route from where it stands to its target crosses a changed page (bounding boxes). */
    static boolean crosses(MatchState s, int i) {
        PathStore paths = s.paths();
        PathStore.Waypoints w = paths.path(i);
        double x = s.motion().x(i);
        double z = s.motion().z(i);
        for (int k = paths.cursor(i); k < w.count(); k++) {
            if (crosses(s, i, x, z, w.x(k), w.z(k))) {
                return true;
            }
            x = w.x(k);
            z = w.z(k);
        }
        boolean beyond = paths.cursor(i) >= w.count() || w.truncated() || paths.status(i) == PathStatus.PARTIAL;
        return beyond && crosses(s, i, x, z, s.orders().targetX(i), s.orders().targetZ(i));
    }

    static boolean crosses(MatchState s, int i, double x0, double z0, double x1, double z1) {
        int pagesX = s.surfaces().pagesX();
        int minX = (int) Math.floor(Math.min(x0, x1)) >> 4;
        int maxX = (int) Math.floor(Math.max(x0, x1)) >> 4;
        int minZ = (int) Math.floor(Math.min(z0, z1)) >> 4;
        int maxZ = (int) Math.floor(Math.max(z0, z1)) >> 4;
        for (int k = 0; k < s.changedPageCount(s.mobility(i)); k++) {
            int page = s.changedPage(k, s.mobility(i));
            int px = page % pagesX;
            int pz = page / pagesX;
            if (px >= minX && px <= maxX && pz >= minZ && pz <= maxZ) {
                return true;
            }
        }
        return false;
    }
}
