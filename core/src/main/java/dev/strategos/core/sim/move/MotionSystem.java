package dev.strategos.core.sim.move;

import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.HotPath;
import dev.strategos.core.sim.Arrived;
import dev.strategos.core.sim.BodyFrame;
import dev.strategos.core.sim.EntityStore;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MatchSystem;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.PathInvalidated;
import dev.strategos.core.sim.Phase;
import dev.strategos.core.sim.PlaceMoved;
import dev.strategos.core.sim.Stagger;
import dev.strategos.core.sim.StopOrdered;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.nav.Dir;
import dev.strategos.core.sim.nav.LineOfWalk;
import dev.strategos.core.sim.nav.PathStatus;
import dev.strategos.core.sim.nav.PathStore;
import dev.strategos.core.sim.nav.Surface;
import dev.strategos.core.sim.nav.SurfaceGrid;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.sim.spatial.UnitFilter;
import dev.strategos.core.sim.stats.StatService;
import java.util.function.IntConsumer;

/**
 * P4: kinematic motion (PATH-05, spec §5.8). While no terrain is published, M0's: every walking unit with a move order
 * steps its walk speed straight toward its next waypoint, or its target while its request is in flight, and lands on it
 * once within one step; height and heading follow M0.
 *
 * <p>On terrain, per walking unit and tick:
 *
 * <ol>
 *   <li>Steering: the next waypoint, passed once within {@value #ADVANCE_WITHIN} block when the one after is in line
 *       of walk from there, or the target while the request waits or is in flight, or after a {@code BUSY} answer
 *       (provisional steering).
 *   <li>Speed: the walk speed, × {@value #SHALLOW} in shallow water, × {@value #SLOW} on slow ground; within one step of
 *       its waypoint or target the unit lands on it exactly, so on flat open ground it moves as in M0.
 *   <li>Avoidance (PATH-06): the step is the unit's preferred velocity, and {@link Avoidance} plans the velocity it
 *       takes around the other units and the tick's agents. Every unit plans before any moves, so the plans see the
 *       same positions; a plan equal to the preferred step is that step, landing included, so a unit with nothing
 *       near moves exactly as without avoidance.
 *   <li>Integration: the new point, and in a new column the surface the current one links to; with no link the move is
 *       tried along X alone, then Z alone, else the unit stays.
 *   <li>Height: an up-step rises halfway on the first tick and onto the new floor on the second, so a block takes two
 *       ticks; a drop falls at most {@value #FALL} block a tick; a surface that vanished drops the unit to the next one
 *       below.
 *   <li>Heading: turns toward the motion by at most the unit's turn rate.
 *   <li>Arrival: landing on a final target posts {@link Arrived}, and the arrival latch holds the unit there: pushed more
 *       than {@value #LATCH} block away, it walks back. Avoidance never keeps a unit off a free target, so it lands
 *       there. A unit {@value #CROWDED} ticks without progress arrives where it stands within {@value #ARRIVE_CROWD}
 *       blocks of its place in a group order (spec §5.8 7) or of a target another unit stands on, or when it touches an
 *       idle unit that arrived at the same target, so a crowd sent to one point settles outward from it.
 *   <li>Stuck monitor: a unit makes progress when it comes {@value #PROGRESS} block nearer its aim than its best, or
 *       passes a waypoint; otherwise, a new route included, its count of ticks without progress grows, and a new order
 *       starts it again. Every {@value #LOST_AFTER} ticks without progress a unit checks its line of walk to its aim,
 *       and asks for a new path once avoidance has pushed it where that line is lost.
 *   <li>Stuck ladder (PATH-07): at its rungs' counts the {@link StuckLadder} asks for a path, walks the unit to a free
 *       spot nearby, walks it with avoidance off, nudges it along its path, and at last fails its order.
 * </ol>
 *
 * Idle units settle: their height follows their floor. Every {@value #IDLE_EVERY} ticks, staggered, an idle unit plans
 * with a zero preferred velocity, so it steps aside for others; once it stood quiet for {@value #QUIET} ticks after
 * that, a latched unit more than {@value #LATCH} block off its point walks back. Every unit that moved or changed
 * height joins the tick's frame, in index order. Speed and turn rate come from the {@link StatService}, so modifiers
 * reach them.
 */
@HotPath
public final class MotionSystem implements MatchSystem {
    private static final byte STANDING = 0;

    /**
     * How far past one step a target may lie and still be reached this tick: rounding leaves a diagonal move's last
     * step a few ulps longer than the speed, which would otherwise cost a whole extra tick (M0 spec 1.6 row 46).
     */
    static final double SNAP_TOLERANCE = 1e-6;

    static final double ADVANCE_WITHIN = 0.5;
    static final double SHALLOW = 0.5;
    static final double SLOW = 0.6;
    static final double FALL = 0.5;
    static final double LATCH = 1.0;
    static final double PROGRESS = 0.05;
    static final int IDLE_EVERY = 4;
    static final int QUIET = 20;
    static final int LOST_AFTER = 20;
    static final double ARRIVE_CROWD = 1.5;

    static final double PASS_CROWD = 2.0;

    static final int CROWDED = 10;
    /** How near two bodies count as touching, beyond their radii. */
    static final double TOUCH = 0.1;
    /** The largest unit radius the touch query looks for, in blocks. */
    private static final double MAX_RADIUS = 1;

    private static final byte SETTLING = 0;
    private static final byte WALKING = 1;
    private static final byte SPENT = 2; // walking, with no waypoint and no request: nowhere to go this tick

    private static final byte LANDS = 1;
    private static final byte FOLLOWING = 2;
    private static final byte LAST = 4;
    private static final byte DETOUR = 8;

    private final MatchState state;
    private final Avoidance avoidance;
    private final RestingGround restingGround = new RestingGround();
    private final StuckLadder ladder = new StuckLadder();
    private final IntConsumer toucher = this::touch;
    private final IntConsumer taker = this::take;
    private int touchSelf;
    private double touchTargetX;
    private double touchTargetZ;
    private double touchRadius;
    private boolean touched;

    // per unit index, from the planning pass to the moving pass
    private byte[] mode = new byte[0];
    private byte[] flags = new byte[0];
    private boolean[] avoiding = new boolean[0];
    private double[] startY = new double[0];
    private double[] aimX = new double[0];
    private double[] aimZ = new double[0];
    private double[] endX = new double[0];
    private double[] endZ = new double[0];
    private double[] planX = new double[0];
    private double[] speed = new double[0];
    private double[] planZ = new double[0];

    public MotionSystem(MatchState state) {
        this.state = state;
        this.avoidance = new Avoidance(state.motion());
        state.bus().subscribe(OrderReceived.class, m -> release(Handle.index(m.handle())));
        state.bus().subscribe(StopOrdered.class, m -> release(Handle.index(m.handle())));
        state.bus().subscribe(PlaceMoved.class, m -> release(Handle.index(m.handle())));
    }

    @Override
    public Phase phase() {
        return Phase.MOVEMENT;
    }

    @Override
    public void run(MatchState s) {
        if (s.surfaces().version() == 0) {
            runFlat(s);
            return;
        }
        EntityStore entities = s.entities();
        OrderQueues orders = s.orders();
        int highWater = entities.highWater();
        ensure(highWater);
        for (int i = 0; i < highWater; i++) { // steer: every walker's preferred step, nothing moves yet
            if (!entities.aliveAt(i)) {
                continue;
            }
            if (s.ai().state(i) == UnitState.WALKING && orders.kind(i) != OrderKind.NONE) {
                steer(s, i);
            } else {
                mode[i] = SETTLING;
            }
        }
        avoidance.intents(mode, endX, endZ, WALKING);
        for (int i = 0; i < highWater; i++) { // plan, round the others' places and the walkers' steps
            if (!entities.aliveAt(i)) {
                continue;
            }
            if (mode[i] == WALKING && s.motion().ghostTicks(i) > 0) {
                planX[i] = endX[i] - s.motion().x(i); // the stuck ladder walks it with avoidance off
                planZ[i] = endZ[i] - s.motion().z(i);
            } else if (mode[i] == WALKING) {
                avoidance.plan(
                        s, i, endX[i] - s.motion().x(i), endZ[i] - s.motion().z(i), speed[i], true);
                planX[i] = avoidance.x();
                planZ[i] = avoidance.z();
            } else {
                planIdle(s, i);
            }
        }
        for (int i = 0; i < highWater; i++) { // move
            if (!entities.aliveAt(i)) {
                continue;
            }
            switch (mode[i]) {
                case WALKING -> stride(s, i);
                case SPENT -> {
                    if (s.paths().status(i) == PathStatus.NO_PATH) {
                        arrive(s, i, entities.handleAt(i)); // nowhere nearer to go
                    }
                    settle(s, i); // a spent partial path: the next request pass asks again
                }
                default -> settle(s, i);
            }
        }
    }

    /**
     * Displaces unit {@code handle} by up to ({@code dx}, {@code dz}) along walkable surfaces, in steps of at most half a
     * block, each across a link like a walk's, stopping where it is blocked; its height follows on its next ticks. On
     * a match without terrain the unit moves the whole way (M0 has no surfaces to stop it).
     */
    public static void impulse(MatchState s, int handle, double dx, double dz) {
        int i = Handle.index(handle);
        MotionStore motion = s.motion();
        double length = DetMath.length(dx, dz);
        if (length == 0) {
            return;
        }
        if (s.surfaces().version() == 0) {
            motion.set(i, motion.x(i) + dx, motion.y(i), motion.z(i) + dz, motion.yaw(i));
            return;
        }
        int steps = (int) Math.ceil(length / 0.5);
        double sx = dx / steps;
        double sz = dz / steps;
        for (int k = 0; k < steps; k++) {
            double x = motion.x(i);
            double z = motion.z(i);
            if (!move(s, i, x + sx, z + sz)) {
                break;
            }
        }
    }

    /** M0's movement: without terrain no path is asked for, so a unit walks straight at its target. */
    private static void runFlat(MatchState s) {
        EntityStore entities = s.entities();
        MotionStore motion = s.motion();
        OrderQueues orders = s.orders();
        StatService stats = s.stats();
        BodyFrame.Builder frame = s.frame();
        int highWater = entities.highWater();
        for (int i = 0; i < highWater; i++) {
            if (!entities.aliveAt(i) || s.ai().state(i) != UnitState.WALKING || orders.kind(i) == OrderKind.NONE) {
                continue;
            }
            int handle = entities.handleAt(i);
            double tx = orders.targetX(i);
            double tz = orders.targetZ(i);
            double speed = stats.walkSpeed(i);
            double x = motion.x(i);
            double y = motion.y(i);
            double z = motion.z(i);
            double dx = tx - x;
            double dz = tz - z;
            double d = DetMath.length(dx, dz);
            if (d <= speed + SNAP_TOLERANCE) {
                float yaw = d > 0 ? DetMath.yawDegrees(dx, dz) : motion.yaw(i);
                motion.set(i, tx, y, tz, yaw);
                frame.add(handle, tx, y, tz, yaw, STANDING);
                s.bus().publish(new Arrived(handle));
            } else {
                double nx = x + dx / d * speed;
                double nz = z + dz / d * speed;
                float yaw = DetMath.yawDegrees(dx, dz);
                motion.set(i, nx, y, nz, yaw);
                frame.add(handle, nx, y, nz, yaw, STANDING);
            }
        }
    }

    /** The steering pass for a walking unit: its aim and preferred step; only its own path cursor and floor change. */
    private void steer(MatchState s, int i) {
        MotionStore motion = s.motion();
        PathStore paths = s.paths();
        OrderQueues orders = s.orders();
        double x0 = motion.x(i);
        double z0 = motion.z(i);
        startY[i] = motion.y(i);
        PathStore.Waypoints path = paths.path(i);
        int cursor = paths.cursor(i);
        while (cursor < path.count() - 1 || (cursor < path.count() && !ends(path, paths.status(i)))) {
            // an intermediate waypoint within half a block is passed, if the next is in line of walk from here; in a
            // crowd that kept the unit from it for CROWDED ticks, within PASS_CROWD
            double d = DetMath.length(path.x(cursor) - x0, path.z(cursor) - z0);
            boolean near = d <= ADVANCE_WITHIN || (motion.stuck(i) >= CROWDED && d <= PASS_CROWD);
            if (!near || (cursor + 1 < path.count() && !inLine(s, i, path.x(cursor + 1), path.z(cursor + 1)))) {
                break;
            }
            paths.advance(i);
            cursor++;
            passed(motion, i);
        }
        boolean following = cursor < path.count();
        double tx;
        double tz;
        boolean last;
        if (following) {
            tx = path.x(cursor);
            tz = path.z(cursor);
            last = cursor == path.count() - 1 && ends(path, paths.status(i));
        } else if (paths.request(i) != 0 || paths.status(i) == PathStatus.BUSY) {
            tx = orders.targetX(i); // provisional steering, while its request waits or the queue was full
            tz = orders.targetZ(i);
            last = true;
        } else {
            mode[i] = SPENT;
            return;
        }
        ground(s, i);
        int stuck = motion.stuck(i);
        if (stuck >= LOST_AFTER
                && stuck % LOST_AFTER == 0
                && stuck != StuckLadder.REPATH // the ladder asked at that count already
                && !paths.repath(i)
                && !inLine(s, i, tx, tz)) {
            // pushed off its route behind something: WALKING has the request pass ask again
            s.bus().publish(new PathInvalidated(s.entities().handleAt(i)));
        }
        double speed = s.stats().walkSpeed(i) * speedFactor(s, i);
        boolean detour = motion.detourTicks(i) > 0; // the stuck ladder's walk to a free spot, steered instead
        double sx = detour ? motion.detourX(i) : tx;
        double sz = detour ? motion.detourZ(i) : tz;
        double dx = sx - x0;
        double dz = sz - z0;
        double d = DetMath.length(dx, dz);
        boolean lands = d <= speed + SNAP_TOLERANCE;
        double nx = lands ? sx : x0 + dx / d * speed;
        double nz = lands ? sz : z0 + dz / d * speed;
        mode[i] = WALKING;
        avoiding[i] = false;
        flags[i] = detour ? DETOUR : (byte) ((lands ? LANDS : 0) | (following ? FOLLOWING : 0) | (last ? LAST : 0));
        aimX[i] = tx;
        aimZ[i] = tz;
        endX[i] = nx;
        endZ[i] = nz;
        this.speed[i] = speed;
    }

    /** The planning pass for a unit that does not walk: every {@value #IDLE_EVERY} ticks it plans to stand still. */
    private void planIdle(MatchState s, int i) {
        avoiding[i] = Stagger.selects(s.tick(), i, IDLE_EVERY);
        if (avoiding[i]) {
            avoidance.planIdle(s, i);
            planX[i] = avoidance.x();
            planZ[i] = avoidance.z();
        }
    }

    /** The moving pass for a walking unit: its planned velocity, or its preferred step exactly when they agree. */
    private void stride(MatchState s, int i) {
        MotionStore motion = s.motion();
        PathStore paths = s.paths();
        int handle = s.entities().handleAt(i);
        double x0 = motion.x(i);
        double z0 = motion.z(i);
        float yaw0 = motion.yaw(i);
        double tx = aimX[i];
        double tz = aimZ[i];
        boolean preferred = planX[i] == endX[i] - x0 && planZ[i] == endZ[i] - z0;
        double nx = preferred ? endX[i] : x0 + planX[i];
        double nz = preferred ? endZ[i] : z0 + planZ[i];
        boolean moved = move(s, i, nx, nz);
        double x = motion.x(i);
        double z = motion.z(i);
        motion.setVelocity(i, x - x0, z - z0);
        float yaw =
                turn(yaw0, x - x0, z - z0, tx - x0, tz - z0, (float) s.stats().turnRate(i));
        motion.set(i, x, motion.y(i), z, yaw);
        Footing.height(s, i);
        monitor(s, i, tx, tz);
        StuckLadder.countDown(motion, i);
        boolean landed = preferred && (flags[i] & LANDS) != 0 && x == tx && z == tz;
        if (!landed && (flags[i] & LAST) != 0) {
            double d = DetMath.length(tx - x, tz - z);
            landed = motion.stuck(i) >= CROWDED
                    && ((d <= ARRIVE_CROWD && (s.orders().group(i) != 0 || taken(s, i, tx, tz)))
                            || touchesArrived(s, i, tx, tz)
                            || tradePlace(s, i, x, z, d));
        }
        if (landed) {
            if ((flags[i] & FOLLOWING) != 0) {
                paths.advance(i);
                if ((flags[i] & LAST) != 0) {
                    arrive(s, i, handle);
                } else {
                    passed(motion, i);
                }
            } else {
                arrive(s, i, handle);
            }
        } else {
            ladder.climb(s, i, handle); // a nudge displaces it
        }
        if (moved || motion.x(i) != x || motion.z(i) != z || motion.y(i) != startY[i] || yaw != yaw0) {
            s.frame().add(handle, motion.x(i), motion.y(i), motion.z(i), yaw, STANDING);
        }
    }

    /**
     * A unit that does not walk: its height follows its floor, and it takes its planned step aside. A latched unit
     * that stood quiet for {@value #QUIET} ticks after its last step aside, more than {@value #LATCH} block off its
     * point, walks back.
     */
    private void settle(MatchState s, int i) {
        MotionStore motion = s.motion();
        int handle = s.entities().handleAt(i);
        double x0 = motion.x(i);
        double y0 = motion.y(i);
        double z0 = motion.z(i);
        boolean pushed = false;
        if (restingGround.ground(s, i)) {
            if (avoiding[i] && (planX[i] != 0 || planZ[i] != 0)) {
                move(s, i, x0 + planX[i], z0 + planZ[i], true);
                pushed = motion.x(i) != x0 || motion.z(i) != z0;
            }
            motion.setVelocity(i, motion.x(i) - x0, motion.z(i) - z0);
            restingGround.height(s, i);
            if (pushed || motion.y(i) != y0) {
                s.frame().add(handle, motion.x(i), motion.y(i), motion.z(i), motion.yaw(i), STANDING);
            }
        }
        if (pushed) {
            motion.setPushed(i, QUIET);
        } else if (motion.pushed(i) > 0) {
            motion.setPushed(i, motion.pushed(i) - 1);
        }
        if (motion.latched(i)
                && motion.pushed(i) == 0
                && s.ai().state(i) == UnitState.IDLE
                && s.orders().kind(i) == OrderKind.NONE // a waiting follower keeps its order: its think moves it
                && DetMath.length(motion.x(i) - motion.latchX(i), motion.z(i) - motion.latchZ(i)) > LATCH) {
            s.orders().replaceMove(i, motion.latchX(i), motion.latchZ(i));
            s.bus().publish(new OrderReceived(handle)); // back to the latch point; the order releases the latch
        }
    }

    /**
     * Whether another unit stands on (tx, tz), its body over the point where unit {@code i}'s would be: an idle one, or
     * one sent to the same point that made no progress for {@value #CROWDED} ticks; not one passing by. Any such unit
     * counts, not only the one nearest the point, which may be {@code i} itself, kept off it by another.
     */
    private boolean taken(MatchState s, int i, double tx, double tz) {
        touchSelf = i;
        touchTargetX = tx;
        touchTargetZ = tz;
        touchRadius = radius(s, i);
        touched = false;
        s.spatial().forEachInRadius(tx, tz, touchRadius + MAX_RADIUS, UnitFilter.ANY, taker);
        return touched;
    }

    private void take(int j) {
        MatchState s = state;
        if (touched || j == touchSelf) {
            return;
        }
        boolean standing = s.ai().state(j) != UnitState.WALKING
                || (s.motion().stuck(j) >= CROWDED
                        && s.orders().targetX(j) == touchTargetX
                        && s.orders().targetZ(j) == touchTargetZ);
        MotionStore motion = s.motion();
        touched = standing
                && DetMath.length(motion.x(j) - touchTargetX, motion.z(j) - touchTargetZ) < touchRadius + radius(s, j);
    }

    /**
     * Whether unit {@code i} touches an idle unit that last walked to the same target (tx, tz): a crowd sent to one
     * point settles outward from it, each unit where it meets the ones that arrived.
     */
    private boolean touchesArrived(MatchState s, int i, double tx, double tz) {
        MotionStore motion = s.motion();
        touchSelf = i;
        touchTargetX = tx;
        touchTargetZ = tz;
        touchRadius = radius(s, i);
        touched = false;
        s.spatial()
                .forEachInRadius(motion.x(i), motion.z(i), touchRadius + MAX_RADIUS + TOUCH, UnitFilter.ANY, toucher);
        return touched;
    }

    private void touch(int j) {
        MatchState s = state;
        MotionStore motion = s.motion();
        if (touched
                || j == touchSelf
                || s.ai().state(j) != UnitState.IDLE
                || motion.aimX(j) != touchTargetX
                || motion.aimZ(j) != touchTargetZ) {
            return;
        }
        double gap = DetMath.length(motion.x(j) - motion.x(touchSelf), motion.z(j) - motion.z(touchSelf));
        touched = gap < touchRadius + radius(s, j) + TOUCH;
    }

    private static double radius(MatchState s, int i) {
        return s.templates().byIndex(s.identity().templateIndex(i)).radius();
    }

    private void ensure(int size) {
        if (size <= mode.length) {
            return;
        }
        int capacity = Math.max(size, mode.length * 2);
        restingGround.ensure(capacity);
        mode = new byte[capacity];
        flags = new byte[capacity];
        avoiding = new boolean[capacity];
        startY = new double[capacity];
        aimX = new double[capacity];
        aimZ = new double[capacity];
        endX = new double[capacity];
        endZ = new double[capacity];
        planX = new double[capacity];
        speed = new double[capacity];
        planZ = new double[capacity];
    }

    private void arrive(MatchState s, int i, int handle) {
        MotionStore motion = s.motion();
        motion.setLatch(i, true, motion.x(i), motion.z(i));
        motion.setVelocity(i, 0, 0);
        motion.setDetour(i, 0, 0, 0);
        motion.setGhostTicks(i, 0);
        s.bus().publish(new Arrived(handle));
    }

    private void release(int i) {
        MotionStore motion = state.motion();
        if (motion.latched(i)) {
            motion.setLatch(i, false, 0, 0);
        }
        passed(motion, i); // a new walk: its first tick sets the best
        motion.setDetour(i, 0, 0, 0);
        motion.setGhostTicks(i, 0);
    }

    /**
     * A group member kept {@value #CROWDED} ticks or more from its place in a crowd takes, every {@value #CROWDED}
     * ticks, the place of a member still walking to one within {@value #ARRIVE_CROWD} blocks of it, the nearest, if
     * nearer than its own; that member is given its place and asks for a path there. Units of a formation do not care
     * which place is whose, and a crowd that pushed one past its place fills up anyway. True when it traded, so it
     * arrives where it stands.
     */
    private boolean tradePlace(MatchState s, int i, double x, double z, double d) {
        OrderQueues orders = s.orders();
        int group = orders.group(i);
        if (group == 0 || s.motion().stuck(i) % CROWDED != 0) {
            return false;
        }
        int best = -1;
        double nearest = Math.min(d, ARRIVE_CROWD);
        for (int j = 0; j < s.entities().highWater(); j++) {
            if (j == i
                    || !s.entities().aliveAt(j)
                    || orders.group(j) != group
                    || orders.kind(j) != orders.kind(i)
                    || s.ai().state(j) != UnitState.WALKING) {
                continue;
            }
            double to = DetMath.length(orders.targetX(j) - x, orders.targetZ(j) - z);
            if (to < nearest) {
                nearest = to;
                best = j;
            }
        }
        if (best < 0) {
            return false;
        }
        orders.tradePlaces(i, best);
        MotionStore motion = s.motion();
        // it arrives aiming at the place it took, so a crowd round the one it gave does not settle on it
        motion.setProgress(i, orders.targetX(i), orders.targetZ(i), motion.best(i), motion.stuck(i));
        s.paths().follow(best, PathStore.Waypoints.NONE, PathStatus.OK); // its way led to the place it gave
        s.paths().want(best); // the next request pass asks for a path to the place it got
        mode[best] = SPENT; // and it does not take this tick's step, planned toward the place it gave
        return true;
    }

    /** Unit {@code i} passed a waypoint, or starts a walk: progress, so its next tick measures from scratch. */
    private static void passed(MotionStore motion, int i) {
        motion.setProgress(i, motion.aimX(i), motion.aimZ(i), Double.POSITIVE_INFINITY, 0);
    }

    /**
     * Makes sure the unit's floor is a surface of its column: the same floor where it still exists, else the highest
     * passable surface at most a quarter block above the floor (or the unit's feet, for a unit that never stood on one),
     * else the lowest passable surface; false when the column has none.
     */
    static boolean ground(MatchState s, int i) {
        MotionStore motion = s.motion();
        SurfaceGrid grid = s.surfaces();
        int cx = (int) Math.floor(motion.x(i));
        int cz = (int) Math.floor(motion.z(i));
        int floor = motion.floor(i);
        int count = grid.count(cx, cz);
        if (floor != MotionStore.NO_FLOOR && layerOf(grid, cx, cz, floor) >= 0) {
            return true;
        }
        int reference = floor != MotionStore.NO_FLOOR
                ? floor
                : (int) Math.round((motion.y(i) - s.config().bounds().minY()) * 16);
        int below = MotionStore.NO_FLOOR;
        int above = MotionStore.NO_FLOOR;
        for (int layer = 0; layer < count; layer++) {
            int surface = grid.surface(cx, cz, layer);
            if (!Surface.passable(surface)) {
                continue;
            }
            int f = Surface.floor(surface);
            if (f <= reference + 4) {
                below = Math.max(below, f);
            } else if (above == MotionStore.NO_FLOOR || f < above) {
                above = f;
            }
        }
        int chosen = below != MotionStore.NO_FLOOR ? below : above;
        if (chosen == MotionStore.NO_FLOOR) {
            return false;
        }
        if (floor == MotionStore.NO_FLOOR) {
            double y = s.config().bounds().minY() + chosen / 16.0; // first contact: stand on it at once
            motion.set(i, motion.x(i), y, motion.z(i), motion.yaw(i));
        }
        motion.setFloor(i, chosen);
        return true;
    }

    /**
     * Moves unit {@code i} toward (nx, nz) across at most one column edge: through the link from its surface, else
     * along X alone, else along Z alone; false when it stays. A unit on no surface moves straight, as in M0.
     */
    private static boolean move(MatchState s, int i, double nx, double nz) {
        return move(s, i, nx, nz, false);
    }

    private static boolean move(MatchState s, int i, double nx, double nz, boolean grounded) {
        MotionStore motion = s.motion();
        double x = motion.x(i);
        double z = motion.z(i);
        if (nx == x && nz == z) {
            return false;
        }
        if (!grounded && !ground(s, i)) {
            motion.set(i, nx, motion.y(i), nz, motion.yaw(i));
            return true;
        }
        if (tryMove(s, i, nx, nz) || tryMove(s, i, nx, z) || tryMove(s, i, x, nz)) {
            return true;
        }
        return false;
    }

    private static boolean tryMove(MatchState s, int i, double nx, double nz) {
        MotionStore motion = s.motion();
        double x = motion.x(i);
        double z = motion.z(i);
        if (nx == x && nz == z) {
            return false;
        }
        SurfaceGrid grid = s.surfaces();
        int cx = (int) Math.floor(x);
        int cz = (int) Math.floor(z);
        int ncx = (int) Math.floor(nx);
        int ncz = (int) Math.floor(nz);
        if (ncx == cx && ncz == cz) {
            motion.set(i, nx, motion.y(i), nz, motion.yaw(i));
            return true;
        }
        if (Math.abs(ncx - cx) > 1 || Math.abs(ncz - cz) > 1) {
            return false; // faster than a column a tick: never at M1's speeds
        }
        int layer = layerOf(grid, cx, cz, motion.floor(i));
        int nl = grid.neighbour(cx, cz, layer, direction(ncx - cx, ncz - cz));
        if (nl < 0) {
            return false;
        }
        motion.setFloor(i, Surface.floor(grid.surface(ncx, ncz, nl)));
        motion.set(i, nx, motion.y(i), nz, motion.yaw(i));
        return true;
    }

    /**
     * The stuck monitor: progress is coming {@value #PROGRESS} block nearer the aim than the best so far, or passing a
     * waypoint ({@link #passed}); a new aim from a new route is not, so the count goes on.
     */
    private static void monitor(MatchState s, int i, double tx, double tz) {
        MotionStore motion = s.motion();
        double d = DetMath.length(tx - motion.x(i), tz - motion.z(i));
        if (motion.best(i) == Double.POSITIVE_INFINITY) {
            motion.setProgress(i, tx, tz, d, 0); // the first tick of a walk, or after a waypoint
        } else if (tx != motion.aimX(i) || tz != motion.aimZ(i)) {
            motion.setProgress(i, tx, tz, d, motion.stuck(i) + 1); // a new route
        } else if (d < motion.best(i) - PROGRESS) {
            motion.setProgress(i, tx, tz, d, 0);
        } else {
            motion.setProgress(i, tx, tz, motion.best(i), motion.stuck(i) + 1);
        }
    }

    /** The heading turned toward the motion (or the target, standing) by at most {@code rate} degrees. */
    private static float turn(float yaw, double vx, double vz, double dx, double dz, float rate) {
        double fx = vx != 0 || vz != 0 ? vx : dx;
        double fz = vx != 0 || vz != 0 ? vz : dz;
        if (fx == 0 && fz == 0) {
            return yaw;
        }
        float wanted = DetMath.yawDegrees(fx, fz);
        float delta = wanted - yaw;
        while (delta > 180f) {
            delta -= 360f;
        }
        while (delta <= -180f) {
            delta += 360f;
        }
        if (delta > rate) {
            delta = rate;
        } else if (delta < -rate) {
            delta = -rate;
        }
        float turned = yaw + delta;
        while (turned > 180f) {
            turned -= 360f;
        }
        while (turned <= -180f) {
            turned += 360f;
        }
        return turned;
    }

    static double idleSpeed(MatchState s, int i) {
        return s.stats().walkSpeed(i) * speedFactor(s, i);
    }

    /** The surface's effect on speed: shallow water halves it, slow ground takes it to six tenths. */
    private static double speedFactor(MatchState s, int i) {
        MotionStore motion = s.motion();
        if (motion.floor(i) == MotionStore.NO_FLOOR) {
            return 1;
        }
        SurfaceGrid grid = s.surfaces();
        int cx = (int) Math.floor(motion.x(i));
        int cz = (int) Math.floor(motion.z(i));
        int layer = layerOf(grid, cx, cz, motion.floor(i));
        if (layer < 0) {
            return 1;
        }
        return switch (Surface.kind(grid.surface(cx, cz, layer))) {
            case Surface.SHALLOW_WATER -> SHALLOW;
            case Surface.SLOW -> SLOW;
            default -> 1;
        };
    }

    /** Whether unit {@code i} can walk straight from where it stands to (tx, tz). */
    private static boolean inLine(MatchState s, int i, double tx, double tz) {
        MotionStore motion = s.motion();
        SurfaceGrid grid = s.surfaces();
        int cx = (int) Math.floor(motion.x(i));
        int cz = (int) Math.floor(motion.z(i));
        int layer = motion.floor(i) == MotionStore.NO_FLOOR ? -1 : layerOf(grid, cx, cz, motion.floor(i));
        return layer >= 0 && LineOfWalk.walk(grid, grid.node(cx, cz, layer), motion.x(i), motion.z(i), tx, tz) >= 0;
    }

    /** The layer of the column's surface with this floor, or -1. */
    static int layerOf(SurfaceGrid grid, int x, int z, int floor) {
        int count = grid.count(x, z);
        for (int layer = 0; layer < count; layer++) {
            if (Surface.floor(grid.surface(x, z, layer)) == floor) {
                return layer;
            }
        }
        return -1;
    }

    private static int direction(int dx, int dz) {
        for (int dir = 0; dir < Dir.COUNT; dir++) {
            if (Dir.dx(dir) == dx && Dir.dz(dir) == dz) {
                return dir;
            }
        }
        throw new IllegalArgumentException("not a neighbour: " + dx + ", " + dz);
    }

    /** Whether the last waypoint of {@code path} ends the move: the goal reached, or no way nearer. */
    private static boolean ends(PathStore.Waypoints path, PathStatus status) {
        return status == PathStatus.NO_PATH || (status == PathStatus.OK && !path.truncated());
    }
}
