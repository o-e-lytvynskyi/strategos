package dev.strategos.mod.server.debug;

import dev.strategos.core.diag.PhaseStat;
import dev.strategos.core.diag.PhaseTimers;
import dev.strategos.core.protocol.DebugFeedMsg;
import dev.strategos.core.protocol.DebugLayer;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.nav.PathService;
import dev.strategos.core.sim.nav.PathStore;
import dev.strategos.core.sim.nav.Surface;
import dev.strategos.core.sim.nav.SurfaceGrid;
import dev.strategos.core.sim.nav.SurfacePage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * What each layer of the movement overlay carries, read from a match's queries (PATH-10): functions of the core's
 * read-only views and an area, so L2 checks them on a harness match with no level. A mark's tier is the unit's
 * activity tier (UNIT-05).
 */
public final class DebugFrames {
    private DebugFrames() {}

    /** A rectangle of a match's columns, {@code [minX, maxX) x [minZ, maxZ)}. */
    public record Area(int minX, int minZ, int maxX, int maxZ) {
        /** The square {@code radius} columns each way around match-local ({@code x}, {@code z}), cut to the match. */
        public static Area around(double x, double z, int radius, int sizeX, int sizeZ) {
            int cx = (int) Math.floor(x);
            int cz = (int) Math.floor(z);
            return new Area(
                    clamp(cx - radius, sizeX), clamp(cz - radius, sizeZ),
                    clamp(cx + radius, sizeX), clamp(cz + radius, sizeZ));
        }

        public boolean isEmpty() {
            return minX >= maxX || minZ >= maxZ;
        }

        public boolean contains(double x, double z) {
            return x >= minX && x < maxX && z >= minZ && z < maxZ;
        }

        private static int clamp(int v, int size) {
            return Math.max(0, Math.min(size, v));
        }
    }

    /**
     * One page's surfaces: on {@link DebugLayer#SURFACES} each one's kind and layer in its column, on {@link
     * DebugLayer#COMPONENTS} its component. Columns of the page beyond the match's edge are left out.
     */
    public static DebugFeedMsg.Cells cells(
            SurfaceGrid grid, DebugFeedMsg.Origin origin, int pageX, int pageZ, DebugLayer layer) {
        SurfacePage page = grid.page(pageX, pageZ);
        List<DebugFeedMsg.Cell> cells = new ArrayList<>();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int localX = pageX * 16 + x;
                int localZ = pageZ * 16 + z;
                if (!grid.inside(localX, localZ)) {
                    continue;
                }
                int column = SurfacePage.column(x, z);
                for (int l = 0; l < page.count(column); l++) {
                    int surface = page.surface(column, l);
                    int value = layer == DebugLayer.COMPONENTS
                            ? grid.component(grid.node(localX, localZ, l))
                            : Surface.kind(surface) | (l << 3);
                    cells.add(new DebugFeedMsg.Cell(column, Surface.floor(surface), value));
                }
            }
        }
        return new DebugFeedMsg.Cells(origin, pageX, pageZ, cells);
    }

    /**
     * The paths of the units standing in {@code area}: from each unit through its waypoints ahead, or, while it waits
     * for its first result, straight at its target, as provisional steering walks; a unit with neither has none. As
     * many parts as the bound needs, at least one.
     */
    public static List<DebugFeedMsg.Paths> paths(MatchQueries q, DebugFeedMsg.Origin origin, Area area) {
        List<DebugFeedMsg.PathLine> lines = new ArrayList<>();
        for (int handle : q.unitHandles()) {
            UnitView unit = q.unit(handle);
            PathStore.UnitPath path = q.path(handle);
            if (unit == null || path == null || !area.contains(unit.x(), unit.z())) {
                continue;
            }
            double y = unit.y() - origin.y();
            boolean waiting = path.request() != 0;
            List<DebugFeedMsg.Point> points = new ArrayList<>();
            points.add(DebugFeedMsg.Point.ofBlocks(unit.x(), y, unit.z()));
            PathStore.Waypoints w = path.waypoints();
            for (int k = path.cursor(); k < w.count() && points.size() < DebugFeedMsg.MAX_POINTS; k++) {
                points.add(DebugFeedMsg.Point.ofBlocks(w.x(k), w.floor(k) / 16.0, w.z(k)));
            }
            Vec2d target = unit.target();
            if (points.size() == 1 && waiting && target != null) {
                points.add(DebugFeedMsg.Point.ofBlocks(target.x(), y, target.z()));
            }
            if (points.size() > 1) {
                lines.add(new DebugFeedMsg.PathLine(handle, path.status().code(), waiting, points));
            }
        }
        return parts(lines, DebugFeedMsg.MAX_LINES, part -> new DebugFeedMsg.Paths(origin, part));
    }

    /** A marker per unit standing in {@code area}; as many parts as the bound needs, at least one. */
    public static List<DebugFeedMsg.Marks> marks(MatchQueries q, DebugFeedMsg.Origin origin, Area area) {
        List<DebugFeedMsg.Mark> marks = new ArrayList<>();
        for (int handle : q.unitHandles()) {
            UnitView unit = q.unit(handle);
            MotionStore.Extra motion = q.motion(handle);
            if (unit == null || motion == null || !area.contains(unit.x(), unit.z())) {
                continue;
            }
            DebugFeedMsg.Point at = DebugFeedMsg.Point.ofBlocks(unit.x(), unit.y() - origin.y(), unit.z());
            marks.add(new DebugFeedMsg.Mark(handle, at, q.tier(handle).code(), motion.stuck(), motion.rung()));
        }
        return parts(marks, DebugFeedMsg.MAX_MARKS, part -> new DebugFeedMsg.Marks(origin, part));
    }

    /** The match's numbers: units, the path service's counts and latency, and every phase timer's 95th percentile. */
    public static DebugFeedMsg.Panel panel(int matchId, MatchQueries q, PhaseTimers timers) {
        PathService.Stats s = q.pathStats();
        List<DebugFeedMsg.Timing> timings = new ArrayList<>();
        for (PhaseStat stat : timers.stats()) {
            timings.add(new DebugFeedMsg.Timing(stat.name(), Math.max(0, stat.p95())));
        }
        return new DebugFeedMsg.Panel(
                matchId,
                Math.max(0, q.tick()),
                q.unitCount(),
                s.inFlight(),
                s.requests(),
                s.decided(),
                s.ok(),
                s.partial(),
                s.noPath(),
                s.superseded(),
                s.expansions(),
                s.maxExpansions(),
                s.latencyP95(),
                s.joinNanos(),
                timings);
    }

    /** {@code items} in parts of at most {@code max}; one empty part for none, so a subscriber's old ones go. */
    private static <T, P> List<P> parts(List<T> items, int max, Function<List<T>, P> part) {
        List<P> parts = new ArrayList<>();
        for (int from = 0; from == 0 || from < items.size(); from += max) {
            parts.add(part.apply(items.subList(from, Math.min(items.size(), from + max))));
        }
        return parts;
    }
}
