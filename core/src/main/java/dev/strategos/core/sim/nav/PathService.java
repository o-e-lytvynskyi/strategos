package dev.strategos.core.sim.nav;

import dev.strategos.core.port.Clock;
import dev.strategos.core.port.JobExecutor;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.Arrived;
import dev.strategos.core.sim.EntityStore;
import dev.strategos.core.sim.MatchBounds;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MatchSystem;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.PathDelivered;
import dev.strategos.core.sim.PathInvalidated;
import dev.strategos.core.sim.Phase;
import dev.strategos.core.sim.StopOrdered;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.OrderQueues;

/**
 * Paths for walking units (ADR-0015 stage 1, 04 §9.3, spec §5.7), in two passes a tick.
 *
 * <p><b>Requests</b> ({@code UNIT_AI}, after the unit AI), in index order: a unit that got a move order this tick asks
 * for a path to its target; one whose truncated path reached its {@value #REASK_AT}th waypoint, or whose path ran out
 * short of the goal, asks again from where it stands; one whose route crosses a page republished with another slice is
 * sent a {@link PathInvalidated}, which its machine answers by marking it (WALKING does), and a marked unit asks again,
 * at most once every {@value #REPATH_EVERY} ticks (PATH-15). While no terrain is published a request does nothing: the
 * unit walks M0's straight line, and a match without terrain keeps M0's state and hashes. A request is decided at once
 * when the unit stands on no surface ({@code NO_PATH}), or when the target lies within {@code shortMove} blocks with
 * line of walk. Any other request takes the next id and submits a {@link PathJob} over the grid as it is now, due
 * {@code pathK} ticks later; meanwhile a unit with no path left steers straight at its target (provisional steering),
 * so every ordered unit moves on the tick its order applies.
 *
 * <p><b>Delivery</b> ({@code INGEST}, after the inputs): {@code STRICT} joins every job due at this tick, in id order,
 * running it here if no worker has; the result becomes the unit's path if the unit still waits for that id, and is
 * dropped as superseded otherwise. A page that changed while a job was in flight may break the path it found on the
 * grid before: such a path is walked on the current grid, and if it fails the unit is sent a {@link PathInvalidated}.
 * Every answer, decided at once or delivered, is posted as a {@link PathDelivered}. Admission, {@code ADAPTIVE}
 * delivery and the worker pool are PATH-16's.
 */
public final class PathService {
    /** A truncated path is asked for again when the unit reaches this waypoint. */
    public static final int REASK_AT = 48;

    /** Changed terrain makes a unit ask again at most once in this many ticks (PATH-15). */
    public static final int REPATH_EVERY = 20;

    private final JobExecutor jobs;
    private final Clock clock;
    private final MatchRules rules;
    private final double shortMoveSquared;
    private final Counters counters = new Counters();

    /** Counts since the match started, for the debug command and the perf tests; not part of the state. */
    @CoreApi
    public record Stats(
            long requests,
            long decided,
            long ok,
            long partial,
            long noPath,
            long superseded,
            long expansions,
            int maxExpansions,
            long latencyTicks,
            long joinNanos,
            int inFlight) {}

    private static final class Counters {
        long requests;
        long decided;
        long ok;
        long partial;
        long noPath;
        long superseded;
        long expansions;
        int maxExpansions;
        long latencyTicks;
        long joinNanos;
    }

    public PathService(MatchState state, JobExecutor jobs, Clock clock) {
        this.jobs = jobs;
        this.clock = clock;
        this.rules = state.config().rules();
        if (rules.delivery() != DeliveryMode.STRICT) {
            throw new IllegalArgumentException("delivery " + rules.delivery().id() + " arrives with PATH-16");
        }
        this.shortMoveSquared = (double) rules.shortMove() * rules.shortMove();
        state.bus().subscribe(OrderReceived.class, m -> state.paths().want(Handle.index(m.handle())));
        state.bus().subscribe(StopOrdered.class, m -> state.paths().clear(Handle.index(m.handle())));
        state.bus().subscribe(Arrived.class, m -> state.paths().clear(Handle.index(m.handle())));
    }

    /** {@code INGEST}: the results due this tick. */
    public MatchSystem delivery() {
        return new MatchSystem() {
            @Override
            public Phase phase() {
                return Phase.INGEST;
            }

            @Override
            public void run(MatchState state) {
                deliver(state);
            }
        };
    }

    /** {@code UNIT_AI}: this tick's requests. */
    public MatchSystem requests() {
        return new MatchSystem() {
            @Override
            public Phase phase() {
                return Phase.UNIT_AI;
            }

            @Override
            public void run(MatchState state) {
                request(state);
            }
        };
    }

    public Stats stats(MatchState state) {
        Counters c = counters;
        return new Stats(
                c.requests,
                c.decided,
                c.ok,
                c.partial,
                c.noPath,
                c.superseded,
                c.expansions,
                c.maxExpansions,
                c.latencyTicks,
                c.joinNanos,
                state.paths().pending().size());
    }

    private void deliver(MatchState s) {
        long tick = s.tick();
        PathStore paths = s.paths();
        PathStore.Pending p;
        while ((p = paths.pollDue(tick)) != null) {
            long start = clock.nanoTime();
            PathResult r = p.job().join();
            counters.joinNanos += clock.nanoTime() - start;
            int handle = p.request().handle();
            int i = Handle.index(handle);
            if (!s.entities().isAlive(handle) || paths.request(i) != r.requestId()) {
                counters.superseded++;
                continue;
            }
            paths.follow(i, startFrom(s, i, p.request(), r.waypoints()), r.status());
            boolean broken = paths.lastPageChange() > p.request().tick() && !walkable(s, i);
            s.bus().publish(new PathDelivered(handle, r.status()));
            if (broken) {
                s.bus().publish(new PathInvalidated(handle)); // terrain changed in flight and broke the route
            }
            count(r.status());
            counters.expansions += r.expansions();
            counters.maxExpansions = Math.max(counters.maxExpansions, r.expansions());
            counters.latencyTicks += tick - p.request().tick();
        }
    }

    private void request(MatchState s) {
        EntityStore entities = s.entities();
        PathStore paths = s.paths();
        OrderQueues orders = s.orders();
        long tick = s.tick();
        boolean changed = s.changedPageCount() > 0;
        int highWater = entities.highWater();
        for (int i = 0; i < highWater; i++) {
            if (!entities.aliveAt(i)) {
                continue;
            }
            boolean wanted = paths.takeWanted(i);
            if (s.ai().state(i) != UnitState.WALKING || orders.kind(i) != OrderKind.MOVE) {
                continue;
            }
            if (wanted) {
                request(s, i, false);
                continue;
            }
            if (paths.request(i) == 0) {
                PathStore.Waypoints w = paths.path(i);
                int cursor = paths.cursor(i);
                if ((w.truncated() && cursor >= REASK_AT)
                        || (cursor >= w.count() && paths.status(i) != PathStatus.NO_PATH)) {
                    request(s, i, true);
                    continue;
                }
            }
            if (changed && !paths.repath(i) && crosses(s, i)) {
                s.bus().publish(new PathInvalidated(entities.handleAt(i))); // the unit's machine marks it to ask again
            }
            long last = paths.lastRequest(i);
            if (paths.repath(i) && (last == PathStore.NEVER || tick - last >= REPATH_EVERY)) {
                request(s, i, true);
            }
        }
    }

    /**
     * Unit {@code i} asks for a path to its move target. {@code keepPath}: it follows its current path until the
     * result (a re-ask or a repath); a new order drops the old path at once.
     */
    private void request(MatchState s, int i, boolean keepPath) {
        PathStore paths = s.paths();
        MotionStore motion = s.motion();
        MatchBounds bounds = s.config().bounds();
        long tick = s.tick();
        double gx = s.orders().targetX(i);
        double gz = s.orders().targetZ(i);
        SurfaceGrid grid = s.surfaces();
        if (grid.version() == 0) {
            return; // no terrain yet: M0's straight line, and no path state, so such a match hashes as in M0
        }
        counters.requests++;
        double x = motion.x(i);
        double z = motion.z(i);
        int from = startNode(grid, x, motion.y(i), z, bounds.minY());
        if (from < 0) {
            decide(s, i, PathStore.Waypoints.NONE, PathStatus.NO_PATH);
            return;
        }
        double dx = gx - x;
        double dz = gz - z;
        if (dx * dx + dz * dz <= shortMoveSquared) {
            int end = LineOfWalk.walk(grid, from, x, z, gx, gz);
            if (end >= 0) {
                int floor = Surface.floor(grid.surface(grid.nodeX(end), grid.nodeZ(end), SurfaceGrid.nodeLayer(end)));
                decide(s, i, PathStore.Waypoints.of(gx, gz, floor), PathStatus.OK);
                return;
            }
        }
        int id = paths.nextRequestId();
        PathRequest r =
                new PathRequest(id, s.entities().handleAt(i), tick, from, x, z, gx, gz, rules.searchCap(), 0, false);
        if (!keepPath) {
            paths.follow(i, PathStore.Waypoints.NONE, PathStatus.OK);
        }
        paths.awaiting(i, id, tick);
        paths.put(new PathStore.Pending(r, tick + rules.pathK(), jobs.submit(new PathJob(grid, r))));
    }

    /** A request answered at once, without an id. */
    private void decide(MatchState s, int i, PathStore.Waypoints w, PathStatus status) {
        s.paths().follow(i, w, status);
        s.paths().decided(i, s.tick());
        counters.decided++;
        count(status);
        s.bus().publish(new PathDelivered(s.entities().handleAt(i), status));
    }

    private void count(PathStatus status) {
        switch (status) {
            case OK -> counters.ok++;
            case PARTIAL -> counters.partial++;
            case NO_PATH -> counters.noPath++;
            case BUSY -> {}
        }
    }

    /**
     * The surface a unit at ({@code x}, {@code y}, {@code z}) stands on: of its column's passable surfaces, the one
     * whose floor is nearest its feet, the upper on a tie; -1 if the column has none.
     */
    public static int startNode(SurfaceGrid grid, double x, double y, double z, int minY) {
        int cx = clampColumn(x, grid.sizeX());
        int cz = clampColumn(z, grid.sizeZ());
        int count = grid.count(cx, cz);
        int best = -1;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int layer = 0; layer < count; layer++) {
            int surface = grid.surface(cx, cz, layer);
            if (!Surface.passable(surface)) {
                continue;
            }
            double distance = Math.abs(minY + Surface.floor(surface) / 16.0 - y);
            if (distance < bestDistance) {
                best = layer;
                bestDistance = distance;
            }
        }
        return best < 0 ? -1 : grid.node(cx, cz, best);
    }

    /** The column holding coordinate {@code v}, kept inside {@code 0 .. limit - 1}. */
    static int clampColumn(double v, int limit) {
        return Math.max(0, Math.min(limit - 1, (int) Math.floor(v)));
    }

    /**
     * The waypoints a unit follows from where it now stands: a path searched from where it stood when it asked, which
     * provisional steering may have left, gets that point first when the unit cannot walk straight to its first
     * waypoint.
     */
    private static PathStore.Waypoints startFrom(MatchState s, int i, PathRequest r, PathStore.Waypoints w) {
        if (w.count() == 0 || Double.isNaN(r.fromX())) {
            return w;
        }
        SurfaceGrid grid = s.surfaces();
        double x = s.motion().x(i);
        double z = s.motion().z(i);
        int node = startNode(grid, x, s.motion().y(i), z, s.config().bounds().minY());
        if (node >= 0 && LineOfWalk.walk(grid, node, x, z, w.x(0), w.z(0)) >= 0) {
            return w;
        }
        int keep = Math.min(w.count(), PathStore.Waypoints.MAX - 1);
        double[] xs = new double[keep + 1];
        double[] zs = new double[keep + 1];
        int[] floors = new int[keep + 1];
        xs[0] = r.fromX();
        zs[0] = r.fromZ();
        floors[0] = Surface.floor(
                grid.surface(grid.nodeX(r.fromNode()), grid.nodeZ(r.fromNode()), SurfaceGrid.nodeLayer(r.fromNode())));
        for (int k = 0; k < keep; k++) {
            xs[k + 1] = w.x(k);
            zs[k + 1] = w.z(k);
            floors[k + 1] = w.floor(k);
        }
        return new PathStore.Waypoints(xs, zs, floors, w.truncated() || keep < w.count());
    }

    /** Whether unit {@code i}'s path can still be walked on the current grid, from where it stands. */
    private static boolean walkable(MatchState s, int i) {
        SurfaceGrid grid = s.surfaces();
        PathStore.Waypoints w = s.paths().path(i);
        double x = s.motion().x(i);
        double z = s.motion().z(i);
        int node = startNode(grid, x, s.motion().y(i), z, s.config().bounds().minY());
        for (int k = 0; k < w.count() && node >= 0; k++) {
            node = LineOfWalk.walk(grid, node, x, z, w.x(k), w.z(k));
            x = w.x(k);
            z = w.z(k);
        }
        return node >= 0;
    }

    /** Whether unit {@code i}'s route from where it stands to its target crosses a changed page (bounding boxes). */
    private static boolean crosses(MatchState s, int i) {
        PathStore paths = s.paths();
        PathStore.Waypoints w = paths.path(i);
        double x = s.motion().x(i);
        double z = s.motion().z(i);
        for (int k = paths.cursor(i); k < w.count(); k++) {
            if (crosses(s, x, z, w.x(k), w.z(k))) {
                return true;
            }
            x = w.x(k);
            z = w.z(k);
        }
        boolean beyond = paths.cursor(i) >= w.count() || w.truncated() || paths.status(i) == PathStatus.PARTIAL;
        return beyond && crosses(s, x, z, s.orders().targetX(i), s.orders().targetZ(i));
    }

    private static boolean crosses(MatchState s, double x0, double z0, double x1, double z1) {
        int pagesX = s.surfaces().pagesX();
        int minX = (int) Math.floor(Math.min(x0, x1)) >> 4;
        int maxX = (int) Math.floor(Math.max(x0, x1)) >> 4;
        int minZ = (int) Math.floor(Math.min(z0, z1)) >> 4;
        int maxZ = (int) Math.floor(Math.max(z0, z1)) >> 4;
        for (int k = 0; k < s.changedPageCount(); k++) {
            int page = s.changedPage(k);
            int px = page % pagesX;
            int pz = page / pagesX;
            if (px >= minX && px <= maxX && pz >= minZ && pz <= maxZ) {
                return true;
            }
        }
        return false;
    }
}
