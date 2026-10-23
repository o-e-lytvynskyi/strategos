package dev.strategos.core.sim.nav;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.DetMath;
import java.util.ArrayList;
import java.util.List;

/**
 * Members' paths from their group's (PATH-09, 04 §9.4, spec §5.11), built in the group's job. Each member joins the
 * group's path at the point nearest it, by line of walk from where it stands, or by a search of at most {@code
 * joinCap} expansions, of which a job runs at most {@code maxJoins}. It follows the path at its place's lateral offset,
 * taken across the group's facing (from the path's start to the group's goal): at each point the offset point when it
 * is in line of walk from the one before and sees the path's next point, the path's own point otherwise. Within
 * {@value #SWITCH} blocks of the goal it leaves the path for its own place. A member that cannot join gets no path and
 * asks for one of its own; a member path that cannot reach its place, or comes from a partial group path, is marked
 * truncated, so its unit asks again from its end.
 */
public final class GroupPaths {
    /** Members leave the group's path for their own places this near its goal. */
    public static final double SWITCH = 8;

    /** A member of a group's request, as admission found it: the surface it stands on, where, and its place. */
    @CoreApi
    public record Member(int handle, int fromNode, double x, double z, double placeX, double placeZ) {}

    private GroupPaths() {}

    /** The group's result with each member's path; the joins' expansions are added to the group's. */
    static PathResult members(
            SurfaceGrid grid,
            PathRequest request,
            PathResult group,
            List<Member> members,
            int joinCap,
            int maxJoins,
            GridAStar search) {
        PathStore.Waypoints w = group.waypoints();
        List<MemberPath> out = new ArrayList<>(members.size());
        if (group.status() == PathStatus.NO_PATH || w.count() == 0) {
            for (Member m : members) {
                out.add(new MemberPath(
                        m.handle(), m.fromNode(), m.x(), m.z(), MemberPath.NONE, PathStore.Waypoints.NONE));
            }
            return new PathResult(group.requestId(), group.status(), w, group.expansions(), out);
        }
        int n = w.count() + 1;
        double[] px = new double[n];
        double[] pz = new double[n];
        px[0] = request.fromX();
        pz[0] = request.fromZ();
        for (int k = 0; k < w.count(); k++) {
            px[k + 1] = w.x(k);
            pz[k + 1] = w.z(k);
        }
        boolean reaches = group.status() == PathStatus.OK && !w.truncated();
        double fx = request.goalX() - px[0];
        double fz = request.goalZ() - pz[0];
        double facing = DetMath.length(fx, fz);
        double leftX = facing == 0 ? 0 : -fz / facing;
        double leftZ = facing == 0 ? 0 : fx / facing;
        Assembly a = new Assembly(grid, px, pz, n, reaches, w.x(w.count() - 1), w.z(w.count() - 1));
        int joins = maxJoins;
        int expansions = group.expansions();
        for (Member m : members) {
            double lateral = (m.placeX() - request.goalX()) * leftX + (m.placeZ() - request.goalZ()) * leftZ;
            int join = a.join(m, joins > 0, search, joinCap);
            expansions += a.searched;
            if (join == MemberPath.SEARCH) {
                joins--;
            }
            if (join == MemberPath.NONE) {
                out.add(new MemberPath(
                        m.handle(), m.fromNode(), m.x(), m.z(), MemberPath.NONE, PathStore.Waypoints.NONE));
                continue;
            }
            a.follow(lateral, m.placeX(), m.placeZ());
            out.add(new MemberPath(m.handle(), m.fromNode(), m.x(), m.z(), join, a.waypoints()));
        }
        return new PathResult(group.requestId(), group.status(), w, expansions, out);
    }

    /** One member's path at a time: its points, the node and point it has reached, and the segment it joined. */
    private static final class Assembly {
        private final SurfaceGrid grid;
        private final double[] px;
        private final double[] pz;
        private final int n;
        private final boolean reaches;
        private final double goalX;
        private final double goalZ;
        private final double[] xs = new double[PathStore.Waypoints.MAX];
        private final double[] zs = new double[PathStore.Waypoints.MAX];
        private final int[] floors = new int[PathStore.Waypoints.MAX];
        private int count;
        private boolean truncated;
        private int segment;
        private int node;
        private double x;
        private double z;
        int searched;

        Assembly(SurfaceGrid grid, double[] px, double[] pz, int n, boolean reaches, double goalX, double goalZ) {
            this.grid = grid;
            this.px = px;
            this.pz = pz;
            this.n = n;
            this.reaches = reaches;
            this.goalX = goalX;
            this.goalZ = goalZ;
        }

        /** Starts member {@code m}'s path at the group path's point nearest it: how it got there. */
        int join(Member m, boolean maySearch, GridAStar search, int joinCap) {
            count = 0;
            truncated = false;
            searched = 0;
            double best = Double.POSITIVE_INFINITY;
            double qx = px[0];
            double qz = pz[0];
            for (int s = 0; s < n - 1; s++) {
                double dx = px[s + 1] - px[s];
                double dz = pz[s + 1] - pz[s];
                double length2 = dx * dx + dz * dz;
                double t = length2 == 0
                        ? 0
                        : Math.max(0, Math.min(1, ((m.x() - px[s]) * dx + (m.z() - pz[s]) * dz) / length2));
                double cx = px[s] + t * dx;
                double cz = pz[s] + t * dz;
                double d = DetMath.length(m.x() - cx, m.z() - cz);
                if (d <= best) { // ties go to the later segment, nearer the goal
                    best = d;
                    segment = s;
                    qx = cx;
                    qz = cz;
                }
            }
            int at = LineOfWalk.walk(grid, m.fromNode(), m.x(), m.z(), qx, qz);
            if (at >= 0) {
                x = m.x();
                z = m.z();
                node = m.fromNode();
                add(qx, qz, at);
                return MemberPath.LINE;
            }
            if (!maySearch) {
                return MemberPath.NONE;
            }
            GridAStar.Result found = search.search(
                    grid,
                    m.fromNode(),
                    PathService.clampColumn(qx, grid.sizeX()),
                    PathService.clampColumn(qz, grid.sizeZ()),
                    joinCap);
            searched = found.expansions();
            if (found.status() != PathStatus.OK) {
                return MemberPath.NONE;
            }
            PathStore.Waypoints pulled = StringPuller.pull(grid, found.nodes(), true, qx, qz, m.x(), m.z());
            for (int k = 0; k < pulled.count() && count < xs.length; k++) {
                xs[count] = pulled.x(k);
                zs[count] = pulled.z(k);
                floors[count++] = pulled.floor(k);
            }
            x = qx;
            z = qz;
            node = found.nodes()[found.nodes().length - 1];
            return MemberPath.SEARCH;
        }

        /** Along the group's path from the joined segment at {@code lateral}, then to the member's place. */
        void follow(double lateral, double placeX, double placeZ) {
            int k = segment + 1;
            for (; k < n && !truncated; k++) {
                if (reaches && DetMath.length(px[k] - goalX, pz[k] - goalZ) <= SWITCH) {
                    break;
                }
                double dx = px[k] - px[k - 1];
                double dz = pz[k] - pz[k - 1];
                double length = DetMath.length(dx, dz);
                boolean laned = false;
                for (double part = 1; part >= 0.25 && lateral != 0 && !laned && length > 0; part /= 2) {
                    // the whole offset, else half or a quarter of it, so lanes close up in a narrow way
                    double sx = px[k] - dz / length * lateral * part;
                    double sz = pz[k] + dx / length * lateral * part;
                    int shifted = inside(sx, sz) ? LineOfWalk.walk(grid, node, x, z, sx, sz) : -1;
                    if (shifted >= 0
                            && (k + 1 >= n || LineOfWalk.walk(grid, shifted, sx, sz, px[k + 1], pz[k + 1]) >= 0)) {
                        add(sx, sz, shifted);
                        laned = true;
                    }
                }
                if (!laned) {
                    step(px[k], pz[k]);
                }
            }
            if (!reaches || truncated) {
                truncated = true; // short of the place: the unit asks again from the end
                return;
            }
            int at = LineOfWalk.walk(grid, node, x, z, placeX, placeZ);
            for (; at < 0 && k < n && !truncated; k++) { // the place is not in sight: on along the path first
                step(px[k], pz[k]);
                at = LineOfWalk.walk(grid, node, x, z, placeX, placeZ);
            }
            if (at >= 0 && !truncated) {
                add(placeX, placeZ, at);
            } else {
                truncated = true;
            }
        }

        PathStore.Waypoints waypoints() {
            double[] wx = new double[count];
            double[] wz = new double[count];
            int[] wf = new int[count];
            System.arraycopy(xs, 0, wx, 0, count);
            System.arraycopy(zs, 0, wz, 0, count);
            System.arraycopy(floors, 0, wf, 0, count);
            return new PathStore.Waypoints(wx, wz, wf, truncated);
        }

        /** The group path's own point, from the one reached; truncated where even that is not in line of walk. */
        private void step(double tx, double tz) {
            int on = LineOfWalk.walk(grid, node, x, z, tx, tz);
            if (on < 0) {
                truncated = true;
                return;
            }
            add(tx, tz, on);
        }

        private void add(double tx, double tz, int at) {
            if (count == xs.length) {
                truncated = true;
                return;
            }
            xs[count] = tx;
            zs[count] = tz;
            floors[count++] = Surface.floor(grid.surface(grid.nodeX(at), grid.nodeZ(at), SurfaceGrid.nodeLayer(at)));
            x = tx;
            z = tz;
            node = at;
        }

        private boolean inside(double tx, double tz) {
            return tx >= 0 && tz >= 0 && tx < grid.sizeX() && tz < grid.sizeZ();
        }
    }
}
