package dev.strategos.core.sim.formation;

import dev.strategos.core.port.JobScratch;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.nav.GateLinks;
import dev.strategos.core.sim.nav.PathJob;
import dev.strategos.core.sim.nav.PathRequest;
import dev.strategos.core.sim.nav.PathResult;
import dev.strategos.core.sim.nav.PathService;
import dev.strategos.core.sim.nav.PathStatus;
import dev.strategos.core.sim.nav.PathStore;
import dev.strategos.core.sim.nav.SurfaceGrid;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The one path a formation follows (04 §9.4): a single search, run when the formation is made and again when a route
 * that stopped short (a partial path, or one cut at {@value PathStore.Waypoints#MAX} waypoints) has been walked, from the
 * member standing nearest the anchor, on the grid of the most confined mobility among the members. The search is the
 * request's own pure job, run here in the tick it is needed, so its result is a function of the state of that tick and
 * a replay finds it again. Without terrain the route is the straight line (M0's walk).
 */
public final class FormationRoute {
    private FormationRoute() {}

    /** The waypoints after the start (x, z pairs), and whether they stop short of the goal. */
    public static final class Plan {
        private final double[] points;
        private final boolean more;

        Plan(double[] points, boolean more) {
            this.points = points.clone();
            this.more = more;
        }

        public double[] points() {
            return points.clone();
        }

        public boolean more() {
            return more;
        }
    }

    /**
     * The route of a formation from {@code guide} (a unit index) to ({@code goalX}, {@code goalZ}), the most confined
     * member's grid; null when there is no way to a surface near the goal, or the guide stands on none.
     */
    public static @Nullable Plan plan(
            MatchState s, int guide, int confined, double goalX, double goalZ, JobScratch scratch) {
        double fromX = s.motion().x(guide);
        double fromZ = s.motion().z(guide);
        if (s.surfaces().version() == 0) {
            return new Plan(new double[] {goalX, goalZ}, false);
        }
        SurfaceGrid grid = s.surfacesFor(confined);
        int from = PathService.startNode(
                grid,
                s.motion().x(guide),
                s.motion().y(guide),
                s.motion().z(guide),
                s.config().bounds().minY());
        if (from < 0) {
            return null;
        }
        // PATH-12: the formation's faction may cross its own gates and its allies', as its members' own searches do
        GateLinks.Pass gates = s.play().gates().passFor(grid, s.identity().faction(confined), s.allies());
        PathRequest request = new PathRequest(
                0,
                0,
                s.tick(),
                from,
                fromX,
                fromZ,
                goalX,
                goalZ,
                s.config().rules().searchCap(),
                0,
                false);
        PathResult result = new PathJob(grid, request, gates).run(scratch);
        PathStore.Waypoints w = result.waypoints();
        if (result.status() == PathStatus.NO_PATH || w.count() == 0) {
            return null;
        }
        List<Double> points = new ArrayList<>();
        for (int p = 0; p < w.count(); p++) {
            points.add(w.x(p));
            points.add(w.z(p));
        }
        double[] out = new double[points.size()];
        for (int k = 0; k < out.length; k++) {
            out[k] = points.get(k);
        }
        boolean more = result.status() != PathStatus.OK || w.truncated();
        return new Plan(out, more);
    }

    /** The start, then the {@code points} that are not within half a block of the one before. */
    public static double[] join(double startX, double startZ, double[] points) {
        List<Double> out = new ArrayList<>();
        out.add(startX);
        out.add(startZ);
        for (int p = 0; p < points.length; p += 2) {
            int last = out.size() - 2;
            double gap = DetMath.length(points[p] - out.get(last), points[p + 1] - out.get(last + 1));
            if (gap >= 0.5 || (p == points.length - 2 && gap > 0)) { // the end of the route always stays
                out.add(points[p]);
                out.add(points[p + 1]);
            }
        }
        double[] route = new double[out.size()];
        for (int k = 0; k < route.length; k++) {
            route[k] = out.get(k);
        }
        return route;
    }
}
