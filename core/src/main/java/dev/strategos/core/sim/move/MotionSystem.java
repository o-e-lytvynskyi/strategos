package dev.strategos.core.sim.move;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
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
import dev.strategos.core.sim.OrderStore;
import dev.strategos.core.sim.Phase;
import dev.strategos.core.sim.StopOrdered;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.nav.Dir;
import dev.strategos.core.sim.nav.LineOfWalk;
import dev.strategos.core.sim.nav.PathStatus;
import dev.strategos.core.sim.nav.PathStore;
import dev.strategos.core.sim.nav.Surface;
import dev.strategos.core.sim.nav.SurfaceGrid;

/**
 * P4: kinematic motion (PATH-05, spec §5.8). While no terrain is published, M0's: every walking unit with a move order
 * steps its walk speed straight toward its next waypoint, or its target while its request is in flight, and lands on it
 * once within one step; height and heading follow M0.
 *
 * <p>On terrain, per walking unit and tick:
 *
 * <ol>
 *   <li>Steering: the next waypoint, passed once within {@value #ADVANCE_WITHIN} block when the one after is in line
 *       of walk from there, or the target while the request is in flight (provisional steering).
 *   <li>Speed: the walk speed, × {@value #SHALLOW} in shallow water, × {@value #SLOW} on slow ground; within one step of
 *       its waypoint or target the unit lands on it exactly, so on flat open ground it moves as in M0.
 *   <li>Integration: the new point, and in a new column the surface the current one links to; with no link the move is
 *       tried along X alone, then Z alone, else the unit stays.
 *   <li>Height: an up-step rises halfway on the first tick and onto the new floor on the second, so a block takes two
 *       ticks; a drop falls at most {@value #FALL} block a tick; a surface that vanished drops the unit to the next one
 *       below.
 *   <li>Heading: turns toward the motion by at most the template's turn rate.
 *   <li>Arrival: landing on a final target posts {@link Arrived}, and the arrival latch holds the unit there: pushed more
 *       than {@value #LATCH} block away, it walks back.
 *   <li>Stuck monitor: a unit makes progress when it comes {@value #PROGRESS} block nearer its aim than its best;
 *       otherwise its count of ticks without progress grows (the ladder of T23 acts on it).
 * </ol>
 *
 * Idle units settle: their height follows their floor, and a latched unit pushed off its point walks back. Every unit
 * that moved or changed height joins the tick's frame, in index order. This is the one place movement reads the
 * template's speed and turn rate (M1's stat service replaces the read).
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

    private final MatchState state;

    public MotionSystem(MatchState state) {
        this.state = state;
        state.bus().subscribe(OrderReceived.class, m -> release(Handle.index(m.handle())));
        state.bus().subscribe(StopOrdered.class, m -> release(Handle.index(m.handle())));
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
        OrderStore orders = s.orders();
        int highWater = entities.highWater();
        for (int i = 0; i < highWater; i++) {
            if (!entities.aliveAt(i)) {
                continue;
            }
            if (s.ai().state(i) == UnitState.WALKING && orders.kind(i) == OrderStore.MOVE) {
                walk(s, i);
            } else {
                settle(s, i);
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
        OrderStore orders = s.orders();
        TemplateSet templates = s.templates();
        BodyFrame.Builder frame = s.frame();
        int highWater = entities.highWater();
        for (int i = 0; i < highWater; i++) {
            if (!entities.aliveAt(i) || s.ai().state(i) != UnitState.WALKING || orders.kind(i) != OrderStore.MOVE) {
                continue;
            }
            int handle = entities.handleAt(i);
            double tx = orders.targetX(i);
            double tz = orders.targetZ(i);
            double speed = templates.byIndex(s.identity().templateIndex(i)).walkSpeed();
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

    private void walk(MatchState s, int i) {
        MotionStore motion = s.motion();
        PathStore paths = s.paths();
        OrderStore orders = s.orders();
        int handle = s.entities().handleAt(i);
        double x0 = motion.x(i);
        double y0 = motion.y(i);
        double z0 = motion.z(i);
        float yaw0 = motion.yaw(i);
        PathStore.Waypoints path = paths.path(i);
        int cursor = paths.cursor(i);
        while (cursor < path.count() - 1 || (cursor < path.count() && !ends(path, paths.status(i)))) {
            // an intermediate waypoint within half a block is passed, if the next is in line of walk from here
            if (DetMath.length(path.x(cursor) - x0, path.z(cursor) - z0) > ADVANCE_WITHIN
                    || (cursor + 1 < path.count() && !inLine(s, i, path.x(cursor + 1), path.z(cursor + 1)))) {
                break;
            }
            paths.advance(i);
            cursor++;
        }
        boolean following = cursor < path.count();
        double tx;
        double tz;
        boolean last;
        if (following) {
            tx = path.x(cursor);
            tz = path.z(cursor);
            last = cursor == path.count() - 1 && ends(path, paths.status(i));
        } else if (paths.request(i) != 0) {
            tx = orders.targetX(i);
            tz = orders.targetZ(i);
            last = true;
        } else {
            if (paths.status(i) == PathStatus.NO_PATH) {
                arrive(s, i, handle); // nowhere nearer to go
            }
            settle(s, i); // a spent partial path: the next request pass asks again
            return;
        }
        UnitTemplate template = s.templates().byIndex(s.identity().templateIndex(i));
        ground(s, i);
        double speed = template.walkSpeed() * speedFactor(s, i);
        double dx = tx - x0;
        double dz = tz - z0;
        double d = DetMath.length(dx, dz);
        boolean lands = d <= speed + SNAP_TOLERANCE;
        double nx = lands ? tx : x0 + dx / d * speed;
        double nz = lands ? tz : z0 + dz / d * speed;
        boolean moved = move(s, i, nx, nz);
        double x = motion.x(i);
        double z = motion.z(i);
        motion.setVelocity(i, x - x0, z - z0);
        float yaw = turn(yaw0, x - x0, z - z0, dx, dz, (float) template.turnRate());
        motion.set(i, x, motion.y(i), z, yaw);
        height(s, i);
        monitor(s, i, tx, tz);
        if (moved || motion.y(i) != y0 || yaw != yaw0) {
            s.frame().add(handle, x, motion.y(i), z, yaw, STANDING);
        }
        if (lands && x == tx && z == tz) {
            if (following) {
                paths.advance(i);
                if (last) {
                    arrive(s, i, handle);
                }
            } else {
                arrive(s, i, handle);
            }
        }
    }

    /** An idle unit: its height follows its floor; latched and pushed off its point, it walks back. */
    private void settle(MatchState s, int i) {
        MotionStore motion = s.motion();
        int handle = s.entities().handleAt(i);
        double y0 = motion.y(i);
        if (ground(s, i)) {
            motion.setVelocity(i, 0, 0);
            height(s, i);
            if (motion.y(i) != y0) {
                s.frame().add(handle, motion.x(i), motion.y(i), motion.z(i), motion.yaw(i), STANDING);
            }
        }
        if (motion.latched(i)
                && s.ai().state(i) == UnitState.IDLE
                && DetMath.length(motion.x(i) - motion.latchX(i), motion.z(i) - motion.latchZ(i)) > LATCH) {
            s.orders().setMove(i, motion.latchX(i), motion.latchZ(i));
            s.bus().publish(new OrderReceived(handle)); // back to the latch point; the order releases the latch
        }
    }

    private void arrive(MatchState s, int i, int handle) {
        MotionStore motion = s.motion();
        motion.setLatch(i, true, motion.x(i), motion.z(i));
        motion.setVelocity(i, 0, 0);
        s.bus().publish(new Arrived(handle));
    }

    private void release(int i) {
        MotionStore motion = state.motion();
        if (motion.latched(i)) {
            motion.setLatch(i, false, 0, 0);
        }
    }

    /**
     * Makes sure the unit's floor is a surface of its column: the same floor where it still exists, else the highest
     * passable surface at most a quarter block above the floor (or the unit's feet, for a unit that never stood on one),
     * else the lowest passable surface; false when the column has none.
     */
    private static boolean ground(MatchState s, int i) {
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
        MotionStore motion = s.motion();
        double x = motion.x(i);
        double z = motion.z(i);
        if (nx == x && nz == z) {
            return false;
        }
        if (!ground(s, i)) {
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

    /** Height toward the floor: halfway up a step on the first tick and onto it on the second; down at most a half. */
    private static void height(MatchState s, int i) {
        MotionStore motion = s.motion();
        if (motion.floor(i) == MotionStore.NO_FLOOR) {
            return;
        }
        double floorY = s.config().bounds().minY() + motion.floor(i) / 16.0;
        double y = motion.y(i);
        if (floorY > y) {
            if (motion.rising(i)) {
                y = floorY;
                motion.setRising(i, false);
            } else {
                y = y + (floorY - y) / 2;
                motion.setRising(i, true);
            }
        } else {
            y = Math.max(floorY, y - FALL);
            motion.setRising(i, false);
        }
        motion.set(i, motion.x(i), y, motion.z(i), motion.yaw(i));
    }

    /** The stuck monitor: progress is coming {@value #PROGRESS} block nearer the aim than the best so far. */
    private static void monitor(MatchState s, int i, double tx, double tz) {
        MotionStore motion = s.motion();
        double d = DetMath.length(tx - motion.x(i), tz - motion.z(i));
        if (tx != motion.aimX(i) || tz != motion.aimZ(i)) {
            motion.setProgress(i, tx, tz, d, 0); // a new aim
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
    private static int layerOf(SurfaceGrid grid, int x, int z, int floor) {
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
