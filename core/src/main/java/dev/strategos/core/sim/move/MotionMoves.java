package dev.strategos.core.sim.move;

import dev.strategos.core.shared.DetMath;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.nav.Dir;
import dev.strategos.core.sim.nav.GateLinks;
import dev.strategos.core.sim.nav.LineOfWalk;
import dev.strategos.core.sim.nav.PathStatus;
import dev.strategos.core.sim.nav.PathStore;
import dev.strategos.core.sim.nav.Surface;
import dev.strategos.core.sim.nav.SurfaceGrid;
import org.jspecify.annotations.Nullable;

/** The movement helpers of {@link MotionSystem}: the floor, one step across a column edge, the stuck monitor, turning. */
final class MotionMoves {
    private MotionMoves() {}

    /** Unit {@code i} passed a waypoint, or starts a walk: progress, so its next tick measures from scratch. */
    static void passed(MotionStore motion, int i) {
        motion.setProgress(i, motion.aimX(i), motion.aimZ(i), Double.POSITIVE_INFINITY, 0);
    }

    /**
     * Makes sure the unit's floor is a surface of its column: the same floor where it still exists, else the highest
     * passable surface at most a quarter block above the floor (or the unit's feet, for a unit that never stood on one),
     * else the lowest passable surface; false when the column has none.
     */
    static boolean ground(MatchState s, int i) {
        MotionStore motion = s.motion();
        SurfaceGrid grid = s.surfacesFor(i);
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
    static boolean move(MatchState s, int i, double nx, double nz) {
        return move(s, i, nx, nz, false);
    }

    static boolean move(MatchState s, int i, double nx, double nz, boolean grounded) {
        MotionStore motion = s.motion();
        double x = motion.x(i);
        double z = motion.z(i);
        if (nx == x && nz == z) {
            return false;
        }
        if (!grounded && !ground(s, i)) {
            GateLinks.Pass gates = gates(s, i);
            if (gates != null && gates.gate((int) Math.floor(x), (int) Math.floor(z))) {
                return inGate(s, i, gates, nx, nz) || inGate(s, i, gates, nx, z) || inGate(s, i, gates, x, nz);
            }
            motion.set(i, nx, motion.y(i), nz, motion.yaw(i));
            return true;
        }
        if (tryMove(s, i, nx, nz) || tryMove(s, i, nx, z) || tryMove(s, i, x, nz)) {
            return true;
        }
        return false;
    }

    static boolean tryMove(MatchState s, int i, double nx, double nz) {
        MotionStore motion = s.motion();
        double x = motion.x(i);
        double z = motion.z(i);
        if (nx == x && nz == z) {
            return false;
        }
        SurfaceGrid grid = s.surfacesFor(i);
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
            GateLinks.Pass gates = ncx != cx && ncz != cz ? null : gates(s, i);
            if (gates != null && gates.gate(ncx, ncz)) {
                motion.set(i, nx, motion.y(i), nz, motion.yaw(i)); // into the gate, its floor kept
                return true;
            }
            return false;
        }
        motion.setFloor(i, Surface.floor(grid.surface(ncx, ncz, nl)));
        motion.set(i, nx, motion.y(i), nz, motion.yaw(i));
        return true;
    }

    /** What unit {@code i} may walk through of the gates of its allies (PATH-12), null while there are none. */
    static GateLinks.@Nullable Pass gates(MatchState s, int i) {
        return s.play().gates().empty()
                ? null
                : s.play().gates().passFor(s.surfacesFor(i), s.identity().faction(i), s.allies());
    }

    /**
     * One step of a unit standing in a gate's columns (it has no surface there): on through the gate's columns, or out
     * onto a surface within a block of its floor, never into a column the gate does not own that has none.
     */
    private static boolean inGate(MatchState s, int i, GateLinks.Pass gates, double nx, double nz) {
        MotionStore motion = s.motion();
        int ncx = (int) Math.floor(nx);
        int ncz = (int) Math.floor(nz);
        int cx = (int) Math.floor(motion.x(i));
        int cz = (int) Math.floor(motion.z(i));
        if (ncx == cx && ncz == cz) {
            motion.set(i, nx, motion.y(i), nz, motion.yaw(i));
            return true;
        }
        if (Math.abs(ncx - cx) > 1 || Math.abs(ncz - cz) > 1 || (ncx != cx && ncz != cz)) {
            return false;
        }
        if (gates.gate(ncx, ncz)) {
            motion.set(i, nx, motion.y(i), nz, motion.yaw(i));
            return true;
        }
        SurfaceGrid grid = s.surfacesFor(i);
        int floor = motion.floor(i) != MotionStore.NO_FLOOR
                ? motion.floor(i)
                : (int) Math.round(
                        (motion.y(i) - s.config().bounds().minY()) * 16); // a restored unit: its feet, as ground()
        int best = MotionStore.NO_FLOOR;
        for (int layer = 0; layer < grid.count(ncx, ncz); layer++) {
            int surface = grid.surface(ncx, ncz, layer);
            int f = Surface.floor(surface);
            if (Surface.passable(surface)
                    && Math.abs(f - floor) <= Footing.STEP_UP
                    && (best == MotionStore.NO_FLOOR || Math.abs(f - floor) < Math.abs(best - floor))) {
                best = f;
            }
        }
        if (best == MotionStore.NO_FLOOR) {
            return false;
        }
        motion.setFloor(i, best);
        motion.set(i, nx, motion.y(i), nz, motion.yaw(i));
        return true;
    }

    /**
     * The stuck monitor: progress is coming {@value MotionSystem#PROGRESS} block nearer the aim than the best so far, or passing a
     * waypoint ({@link #passed}); a new aim from a new route is not, so the count goes on.
     */
    static void monitor(MatchState s, int i, double tx, double tz) {
        MotionStore motion = s.motion();
        double d = DetMath.length(tx - motion.x(i), tz - motion.z(i));
        if (motion.best(i) == Double.POSITIVE_INFINITY) {
            motion.setProgress(i, tx, tz, d, 0); // the first tick of a walk, or after a waypoint
        } else if (tx != motion.aimX(i) || tz != motion.aimZ(i)) {
            motion.setProgress(i, tx, tz, d, motion.stuck(i) + 1); // a new route
        } else if (d < motion.best(i) - MotionSystem.PROGRESS) {
            motion.setProgress(i, tx, tz, d, 0);
        } else {
            motion.setProgress(i, tx, tz, motion.best(i), motion.stuck(i) + 1);
        }
    }

    /** The heading turned toward the motion (or the target, standing) by at most {@code rate} degrees. */
    static float turn(float yaw, double vx, double vz, double dx, double dz, float rate) {
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
    static double speedFactor(MatchState s, int i) {
        MotionStore motion = s.motion();
        if (motion.floor(i) == MotionStore.NO_FLOOR) {
            return 1;
        }
        SurfaceGrid grid = s.surfacesFor(i);
        int cx = (int) Math.floor(motion.x(i));
        int cz = (int) Math.floor(motion.z(i));
        int layer = layerOf(grid, cx, cz, motion.floor(i));
        if (layer < 0) {
            return 1;
        }
        return switch (Surface.kind(grid.surface(cx, cz, layer))) {
            case Surface.SHALLOW_WATER -> MotionSystem.SHALLOW;
            case Surface.SLOW -> MotionSystem.SLOW;
            default -> 1;
        };
    }

    /** How near a waypoint it has just passed a unit's leg on is checked for being clear, blocks. */
    static final double CORNER = 3;

    /** A unit this near the line of its leg is on it: it steers on, clear leg or not. */
    static final double OFF_LEG = 0.25;

    /**
     * QA-12: just past a waypoint, the straight leg from where the unit now stands to the next one may cross the corner
     * of what the path was routed through (a one-block ramp beside a cliff: the unit passed the turn half a block off
     * its centre, slid along the cliff and walked back to the turn, again and again). While the leg is not clear the
     * unit steers to the passed waypoint's centre; the centre of a waypoint on the path is clear by construction, and
     * on flat ground every leg is, so nothing changes there. Checked only within {@value #CORNER} blocks of the passed
     * waypoint, where the corner is. Null while the leg is clear.
     */
    static double @org.jspecify.annotations.Nullable [] backToCorner(
            MatchState s, int i, PathStore.Waypoints path, int cursor, double x, double z, double tx, double tz) {
        if (cursor < 1 || cursor >= path.count()) {
            return null;
        }
        double px = path.x(cursor - 1);
        double pz = path.z(cursor - 1);
        double away = DetMath.length(x - px, z - pz);
        double legX = tx - px;
        double legZ = tz - pz;
        double leg = DetMath.length(legX, legZ);
        double lateral = leg == 0 ? 0 : Math.abs(legX * (z - pz) - legZ * (x - px)) / leg;
        if (away > CORNER || lateral <= OFF_LEG || inLine(s, i, tx, tz)) {
            return null; // on its leg: an unclear leg is the path's own, and it goes on as before
        }
        // going back helps only if the leg is clear from the corner's centre: else the unit would go to and fro
        SurfaceGrid grid = s.surfacesFor(i);
        int cx = (int) Math.floor(px);
        int cz = (int) Math.floor(pz);
        int layer = layerOf(grid, cx, cz, path.floor(cursor - 1));
        if (layer < 0 || LineOfWalk.walk(grid, grid.node(cx, cz, layer), px, pz, tx, tz, gates(s, i)) < 0) {
            return null;
        }
        return new double[] {px, pz};
    }

    /** Whether unit {@code i} can walk straight from where it stands to (tx, tz). */
    static boolean inLine(MatchState s, int i, double tx, double tz) {
        MotionStore motion = s.motion();
        SurfaceGrid grid = s.surfacesFor(i);
        int cx = (int) Math.floor(motion.x(i));
        int cz = (int) Math.floor(motion.z(i));
        int layer = motion.floor(i) == MotionStore.NO_FLOOR ? -1 : layerOf(grid, cx, cz, motion.floor(i));
        return layer >= 0
                && LineOfWalk.walk(grid, grid.node(cx, cz, layer), motion.x(i), motion.z(i), tx, tz, gates(s, i)) >= 0;
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

    static int direction(int dx, int dz) {
        for (int dir = 0; dir < Dir.COUNT; dir++) {
            if (Dir.dx(dir) == dx && Dir.dz(dir) == dz) {
                return dir;
            }
        }
        throw new IllegalArgumentException("not a neighbour: " + dx + ", " + dz);
    }

    /** Whether the last waypoint of {@code path} ends the move: the goal reached, or no way nearer. */
    static boolean ends(PathStore.Waypoints path, PathStatus status) {
        return status == PathStatus.NO_PATH || (status == PathStatus.OK && !path.truncated());
    }
}
