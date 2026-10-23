package dev.strategos.core.sim.nav;

import dev.strategos.core.diag.RingHistogram;
import dev.strategos.core.port.Clock;
import dev.strategos.core.port.JobExecutor;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.Arrived;
import dev.strategos.core.sim.EntityStore;
import dev.strategos.core.sim.JobDelivery;
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
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
 * line of walk. Any other request takes the next id and waits for admission; meanwhile a unit with no path left steers
 * straight at its target (provisional steering), so every ordered unit moves on the tick its order applies. With
 * {@code maxOutstanding} requests already waiting or in flight, a request is answered {@code BUSY} at once: the unit
 * keeps its path, or steers at its target, and asks again {@code busyRetry} ticks later.
 *
 * <p><b>Admission</b> (PATH-16, at the end of the request pass): waiting requests in id order, while the expansion caps
 * admitted this tick fit the window W ({@code admissionW}); each admitted request becomes a {@link PathJob} over the
 * grid as it is now, from where its unit now stands, submitted to the {@link JobExecutor} and due {@code pathK} ticks
 * later. A request whose unit died or asked again meanwhile is dropped as superseded.
 *
 * <p><b>Delivery</b> ({@code INGEST}, after the inputs), in id order: {@code STRICT} joins every job due at this tick,
 * running it here if no worker has; {@code ADAPTIVE} applies the due jobs the tick's {@link JobDelivery} lists, which
 * the adapter found finished ({@link #finished}), and joins any due {@value #HELP_AFTER} ticks ago. The result becomes
 * the unit's path if the unit still waits for that id, and is dropped as superseded otherwise. A page that changed
 * while a job was in flight may break the path it found on the grid before: such a path is walked on the current grid,
 * and if it fails the unit is sent a {@link PathInvalidated}. Every answer, decided at once or delivered, is posted as
 * a {@link PathDelivered}. The executor's threads never change a result, and in {@code ADAPTIVE} the journaled
 * delivery fixes the timing, so a match plays the same on any number of threads.
 */
public final class PathService {
    /** A truncated path is asked for again when the unit reaches this waypoint. */
    public static final int REASK_AT = 48;

    /** Changed terrain makes a unit ask again at most once in this many ticks (PATH-15). */
    public static final int REPATH_EVERY = 20;

    /** The delivered results whose latency {@link Stats#latencyP95()} covers, and the ticks {@code workP99} does. */
    public static final int LATENCY_WINDOW = 600;

    /** {@code ADAPTIVE} joins a job this many ticks after it was due, finished or not. */
    public static final int HELP_AFTER = 8;

    private final JobExecutor jobs;
    private final Clock clock;
    private final MatchRules rules;
    private final double shortMoveSquared;
    private final Counters counters = new Counters();

    /**
     * Counts since the match started, for the debug command, the overlay and the perf tests; not part of the state.
     * {@code latencyP95}: the 95th percentile of the latency of the last {@value #LATENCY_WINDOW} results applied, in
     * ticks from the unit's request to delivery (PATH-10's panel). {@code waiting}: requests waiting for admission;
     * {@code busy}: requests answered {@code BUSY}; {@code workP99}: the 99th percentile of the main thread's path work
     * a tick (delivery, joins, requests and admission) over the last {@value #LATENCY_WINDOW} ticks, and {@code
     * lastWork} the last tick's, in nanoseconds; {@code unfinished}: results joined before their job had finished, so
     * the main thread ran the job or waited for it. {@code groupRequests}: the requests made for groups, each counted
     * once in {@code requests} too (PATH-09); {@code joins}: members that joined their group's path by a search;
     * {@code unjoined}: members that could not join it and asked for paths of their own.
     */
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
            int inFlight,
            long latencyP95,
            int waiting,
            long busy,
            long workP99,
            long lastWork,
            long unfinished,
            long groupRequests,
            long joins,
            long unjoined) {}

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
        long busy;
        long tickWork;
        long lastWork;
        long unfinished;
        long groupRequests;
        long joins;
        long unjoined;
        final RingHistogram latency = new RingHistogram(LATENCY_WINDOW);
        final RingHistogram work = new RingHistogram(LATENCY_WINDOW);
    }

    public PathService(MatchState state, JobExecutor jobs, Clock clock) {
        this.jobs = jobs;
        this.clock = clock;
        this.rules = state.config().rules();
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
                long start = clock.nanoTime();
                deliver(state);
                counters.tickWork += clock.nanoTime() - start;
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
                long start = clock.nanoTime();
                request(state);
                admit(state);
                counters.lastWork = counters.tickWork + clock.nanoTime() - start;
                counters.work.record(counters.lastWork);
                counters.tickWork = 0;
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
                state.paths().pending().size(),
                c.latency.percentile(0.95),
                state.paths().waiting().size(),
                c.busy,
                c.work.percentile(0.99),
                c.lastWork,
                c.unfinished,
                c.groupRequests,
                c.joins,
                c.unjoined);
    }

    /**
     * For an {@code ADAPTIVE} adapter, before it steps tick {@code tick}: the requests in flight due by then whose jobs
     * have finished, ascending; every one due when the executor has no threads, since its jobs run at their join. The
     * adapter hands them in as that tick's {@link JobDelivery}.
     */
    public int[] finished(MatchState state, long tick) {
        int n = 0;
        int[] ids = new int[state.paths().pending().size()];
        for (PathStore.Pending p : state.paths().pending()) {
            if (p.due() > tick) {
                break; // due ticks never fall as ids rise
            }
            if (jobs.threads() == 0 || p.job().isDone()) {
                ids[n++] = p.request().id();
            }
        }
        return Arrays.copyOf(ids, n);
    }

    private void deliver(MatchState s) {
        long tick = s.tick();
        PathStore paths = s.paths();
        if (rules.delivery() == DeliveryMode.STRICT) {
            PathStore.Pending p;
            while ((p = paths.pollDue(tick)) != null) {
                apply(s, p);
            }
            return;
        }
        int n = 0;
        int[] ready = new int[paths.pending().size()];
        for (PathStore.Pending p : paths.pending()) {
            if (p.due() > tick) {
                break;
            }
            if (p.due() + HELP_AFTER <= tick || s.delivered(p.request().id())) {
                ready[n++] = p.request().id();
            }
        }
        for (int k = 0; k < n; k++) {
            apply(s, paths.take(ready[k]));
        }
    }

    /** A result joined and given to its unit, or a group's to its members, those that still wait for it. */
    private void apply(MatchState s, PathStore.Pending p) {
        if (!p.job().isDone()) {
            counters.unfinished++;
        }
        long start = clock.nanoTime();
        PathResult r = p.job().join();
        counters.joinNanos += clock.nanoTime() - start;
        counters.expansions += r.expansions();
        counters.maxExpansions = Math.max(counters.maxExpansions, r.expansions());
        PathRequest q = p.request();
        if (q.groupId() != 0) {
            for (MemberPath m : r.members()) {
                give(s, m.handle(), q, r, m.fromNode(), m.fromX(), m.fromZ(), m.join(), m.waypoints());
            }
            return;
        }
        give(s, q.handle(), q, r, q.fromNode(), q.fromX(), q.fromZ(), MemberPath.LINE, r.waypoints());
    }

    /**
     * A result's path for one unit, from where it stood when the job was made, if the unit still waits for it; a group
     * member that could not join asks for a path of its own this tick.
     */
    private void give(
            MatchState s,
            int handle,
            PathRequest q,
            PathResult r,
            int fromNode,
            double fromX,
            double fromZ,
            int join,
            PathStore.Waypoints w) {
        PathStore paths = s.paths();
        int i = Handle.index(handle);
        if (!s.entities().isAlive(handle) || paths.request(i) != r.requestId()) {
            counters.superseded++;
            return;
        }
        if (join == MemberPath.NONE) {
            counters.unjoined++;
            paths.want(i); // this tick's request pass asks for its own
            return;
        }
        if (join == MemberPath.SEARCH) {
            counters.joins++;
        }
        long latency = s.tick() - paths.lastRequest(i);
        paths.follow(i, startFrom(s, i, fromNode, fromX, fromZ, w), r.status());
        boolean broken = paths.lastPageChange() > q.tick() && !walkable(s, i);
        s.bus().publish(new PathDelivered(handle, r.status()));
        if (broken) {
            s.bus().publish(new PathInvalidated(handle)); // terrain changed in flight and broke the route
        }
        count(r.status());
        counters.latencyTicks += latency;
        counters.latency.record(latency);
    }

    private void request(MatchState s) {
        EntityStore entities = s.entities();
        PathStore paths = s.paths();
        OrderQueues orders = s.orders();
        long tick = s.tick();
        boolean changed = s.changedPageCount() > 0;
        int highWater = entities.highWater();
        Map<Integer, IntList> groups = grouped(s);
        for (int i = 0; i < highWater; i++) {
            if (!entities.aliveAt(i)) {
                continue;
            }
            boolean wanted = paths.takeWanted(i);
            if (s.ai().state(i) != UnitState.WALKING || orders.kind(i) != OrderKind.MOVE) {
                continue;
            }
            if (wanted) {
                IntList group = groups.get(orders.group(i));
                if (group == null || !group.contains(i)) {
                    request(s, i, false);
                }
                continue;
            }
            if (paths.request(i) == 0 && paths.status(i) == PathStatus.BUSY) {
                if (tick - paths.lastRequest(i) >= rules.busyRetry()) {
                    request(s, i, true); // the queue was full: ask again
                }
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
        for (Map.Entry<Integer, IntList> group : groups.entrySet()) {
            groupRequest(s, group.getKey(), group.getValue());
        }
    }

    /**
     * The groups that ask for one path this tick (PATH-09): of the units given a move order this tick, those of one
     * group order whose own target lies beyond {@code shortMove}, when they are {@code groupMin} or more; by group id,
     * each with its members in index order.
     */
    private Map<Integer, IntList> grouped(MatchState s) {
        Map<Integer, IntList> groups = new TreeMap<>();
        if (s.surfaces().version() == 0) {
            return groups;
        }
        OrderQueues orders = s.orders();
        MotionStore motion = s.motion();
        for (int i = 0; i < s.entities().highWater(); i++) {
            if (!s.entities().aliveAt(i)
                    || !s.paths().wanted(i)
                    || orders.group(i) == 0
                    || s.ai().state(i) != UnitState.WALKING
                    || orders.kind(i) != OrderKind.MOVE) {
                continue;
            }
            double dx = orders.targetX(i) - motion.x(i);
            double dz = orders.targetZ(i) - motion.z(i);
            if (dx * dx + dz * dz > shortMoveSquared) {
                groups.computeIfAbsent(orders.group(i), g -> new IntArrayList()).add(i);
            }
        }
        groups.values().removeIf(members -> members.size() < rules.groupMin());
        return groups;
    }

    /** One request for a group's members: they drop their paths and wait for its id, as one in the queue. */
    private void groupRequest(MatchState s, int group, IntList members) {
        PathStore paths = s.paths();
        counters.requests++;
        for (int k = 0; k < members.size(); k++) {
            paths.follow(members.getInt(k), PathStore.Waypoints.NONE, PathStatus.OK);
        }
        if (paths.outstanding() >= rules.maxOutstanding()) {
            for (int k = 0; k < members.size(); k++) {
                busy(s, members.getInt(k));
            }
            return;
        }
        counters.groupRequests++;
        int id = paths.nextRequestId();
        for (int k = 0; k < members.size(); k++) {
            paths.awaiting(members.getInt(k), id, s.tick());
        }
        paths.enqueue(new PathStore.Waiting(id, s.entities().handleAt(members.getInt(0)), group));
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
        if (!keepPath) {
            paths.follow(i, PathStore.Waypoints.NONE, PathStatus.OK);
        }
        if (paths.outstanding() >= rules.maxOutstanding()) {
            busy(s, i);
            return;
        }
        int id = paths.nextRequestId();
        paths.awaiting(i, id, tick);
        paths.enqueue(new PathStore.Waiting(id, s.entities().handleAt(i)));
    }

    /**
     * Admission: waiting requests in id order while the caps admitted this tick fit W, each a job over the grid as it is
     * now, from where its unit now stands.
     */
    private void admit(MatchState s) {
        PathStore paths = s.paths();
        long spent = 0;
        PathStore.Waiting w;
        while ((w = paths.peekWaiting()) != null) {
            if (w.group() != 0) {
                IntList members = awaiting(s, w.id());
                if (members.size() == 0) {
                    paths.pollWaiting();
                    counters.superseded++;
                    continue;
                }
                int joins = Math.min(members.size(), maxJoins());
                long cost = rules.searchCap() + (long) joins * rules.joinCap();
                if (spent + cost > rules.admissionW()) {
                    return;
                }
                paths.pollWaiting();
                spent += cost;
                submitGroup(s, w, members, joins);
                continue;
            }
            int i = Handle.index(w.handle());
            if (!s.entities().isAlive(w.handle()) || paths.request(i) != w.id()) {
                paths.pollWaiting();
                counters.superseded++;
                continue;
            }
            if (spent + rules.searchCap() > rules.admissionW()) {
                return;
            }
            paths.pollWaiting();
            spent += rules.searchCap();
            submit(s, i, w);
        }
    }

    /** The joins one group's job may search: as many as fit W beside the group's own search. */
    private int maxJoins() {
        return Math.max(0, (rules.admissionW() - rules.searchCap()) / rules.joinCap());
    }

    /** The live units waiting for request {@code id}, in index order. */
    private static IntList awaiting(MatchState s, int id) {
        IntList members = new IntArrayList();
        for (int i = 0; i < s.entities().highWater(); i++) {
            if (s.entities().aliveAt(i) && s.paths().request(i) == id) {
                members.add(i);
            }
        }
        return members;
    }

    /**
     * A group's job: from the surface nearest its members' centroid (or the member nearest it, when the centroid stands
     * on none) to the place nearest the centroid of their places, each member from where it stands now; a member that
     * stands on no surface is answered {@code NO_PATH} at once.
     */
    private void submitGroup(MatchState s, PathStore.Waiting w, IntList members, int joins) {
        MotionStore motion = s.motion();
        OrderQueues orders = s.orders();
        SurfaceGrid grid = s.surfaces();
        int minY = s.config().bounds().minY();
        List<GroupPaths.Member> standing = new ArrayList<>(members.size());
        double cx = 0;
        double cy = 0;
        double cz = 0;
        double px = 0;
        double pz = 0;
        for (int k = 0; k < members.size(); k++) {
            int i = members.getInt(k);
            int from = startNode(grid, motion.x(i), motion.y(i), motion.z(i), minY);
            if (from < 0) {
                decide(s, i, PathStore.Waypoints.NONE, PathStatus.NO_PATH);
                continue;
            }
            standing.add(new GroupPaths.Member(
                    s.entities().handleAt(i), from, motion.x(i), motion.z(i), orders.targetX(i), orders.targetZ(i)));
            cx += motion.x(i);
            cy += motion.y(i);
            cz += motion.z(i);
            px += orders.targetX(i);
            pz += orders.targetZ(i);
        }
        if (standing.isEmpty()) {
            return;
        }
        int n = standing.size();
        cx /= n;
        cy /= n;
        cz /= n;
        px /= n;
        pz /= n;
        int from = startNode(grid, cx, cy, cz, minY);
        double fromX = cx;
        double fromZ = cz;
        GroupPaths.Member goal = standing.get(0);
        GroupPaths.Member nearest = standing.get(0);
        for (GroupPaths.Member m : standing) {
            if (DetMath.length(m.placeX() - px, m.placeZ() - pz)
                    < DetMath.length(goal.placeX() - px, goal.placeZ() - pz)) {
                goal = m;
            }
            if (DetMath.length(m.x() - cx, m.z() - cz) < DetMath.length(nearest.x() - cx, nearest.z() - cz)) {
                nearest = m;
            }
        }
        if (from < 0) {
            from = nearest.fromNode();
            fromX = nearest.x();
            fromZ = nearest.z();
        }
        PathRequest r = new PathRequest(
                w.id(),
                w.handle(),
                s.tick(),
                from,
                fromX,
                fromZ,
                goal.placeX(),
                goal.placeZ(),
                rules.searchCap(),
                w.group(),
                false);
        s.paths()
                .put(new PathStore.Pending(
                        r,
                        s.tick() + rules.pathK(),
                        jobs.submit(new GroupJob(grid, r, standing, rules.joinCap(), joins))));
    }

    private void submit(MatchState s, int i, PathStore.Waiting w) {
        PathStore paths = s.paths();
        MotionStore motion = s.motion();
        SurfaceGrid grid = s.surfaces();
        long tick = s.tick();
        double x = motion.x(i);
        double z = motion.z(i);
        int from = startNode(grid, x, motion.y(i), z, s.config().bounds().minY());
        if (from < 0) {
            decide(s, i, PathStore.Waypoints.NONE, PathStatus.NO_PATH); // it stands on no surface now
            return;
        }
        PathRequest r = new PathRequest(
                w.id(),
                w.handle(),
                tick,
                from,
                x,
                z,
                s.orders().targetX(i),
                s.orders().targetZ(i),
                rules.searchCap(),
                0,
                false);
        paths.put(new PathStore.Pending(r, tick + rules.pathK(), jobs.submit(new PathJob(grid, r))));
    }

    /** The queue is full: the unit keeps its path, or steers at its target, and asks again after {@code busyRetry}. */
    private void busy(MatchState s, int i) {
        s.paths().busy(i, s.tick());
        counters.busy++;
        s.bus().publish(new PathDelivered(s.entities().handleAt(i), PathStatus.BUSY));
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
    private static PathStore.Waypoints startFrom(
            MatchState s, int i, int fromNode, double fromX, double fromZ, PathStore.Waypoints w) {
        if (w.count() == 0 || Double.isNaN(fromX)) {
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
