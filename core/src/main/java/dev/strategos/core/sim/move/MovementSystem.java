package dev.strategos.core.sim.move;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.HotPath;
import dev.strategos.core.sim.Arrived;
import dev.strategos.core.sim.BodyFrame;
import dev.strategos.core.sim.EntityStore;
import dev.strategos.core.sim.IdentityStore;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MatchSystem;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.OrderStore;
import dev.strategos.core.sim.Phase;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.nav.PathStatus;
import dev.strategos.core.sim.nav.PathStore;

/**
 * P4, provisional until PATH-05's motion (T9): every walking unit with a move order steps {@code walkSpeed} toward its
 * next waypoint and lands exactly on it once it is within one step. A unit whose path is spent while its request is in
 * flight steers straight at its target (provisional steering, PATH-03.3), as every unit does while no terrain is
 * published (M0's movement). Landing on the last waypoint of a path that
 * reaches the goal, or of one that cannot get closer ({@code NO_PATH}), ends the move with {@link Arrived}; a partial or
 * truncated path ends with a new request instead (UNIT_AI's next pass). Height stays where it is: following the
 * surfaces is T9's. This is the one place movement reads the template's speed (M1's stat service replaces the read).
 * Every moved body joins the tick's frame, in index order.
 */
@HotPath
public final class MovementSystem implements MatchSystem {
    private static final byte STANDING = 0;

    /**
     * How far past one step a target may lie and still be reached this tick: rounding leaves a diagonal move's last
     * step a few ulps longer than the speed, which would otherwise cost a whole extra tick (M0 spec 1.6 row 46).
     */
    static final double SNAP_TOLERANCE = 1e-6;

    @Override
    public Phase phase() {
        return Phase.MOVEMENT;
    }

    @Override
    public void run(MatchState state) {
        EntityStore entities = state.entities();
        IdentityStore identity = state.identity();
        MotionStore motion = state.motion();
        OrderStore orders = state.orders();
        PathStore paths = state.paths();
        TemplateSet templates = state.templates();
        BodyFrame.Builder frame = state.frame();
        boolean terrain = state.surfaces().version() > 0;
        int highWater = entities.highWater();
        for (int i = 0; i < highWater; i++) {
            if (!entities.aliveAt(i) || state.ai().state(i) != UnitState.WALKING || orders.kind(i) != OrderStore.MOVE) {
                continue;
            }
            int handle = entities.handleAt(i);
            PathStore.Waypoints path = paths.path(i);
            int cursor = paths.cursor(i);
            boolean following = cursor < path.count();
            double tx;
            double tz;
            if (following) {
                tx = path.x(cursor);
                tz = path.z(cursor);
            } else if (paths.request(i) != 0 || !terrain) {
                tx = orders.targetX(i);
                tz = orders.targetZ(i);
            } else {
                if (paths.status(i) == PathStatus.NO_PATH) {
                    state.bus().publish(new Arrived(handle)); // nowhere nearer to go
                }
                continue; // a spent partial path: the next request pass asks again
            }
            double speed = templates.byIndex(identity.templateIndex(i)).walkSpeed();
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
                if (!following) {
                    state.bus().publish(new Arrived(handle));
                } else {
                    paths.advance(i);
                    if (cursor + 1 == path.count() && ends(path, paths.status(i))) {
                        state.bus().publish(new Arrived(handle));
                    }
                }
            } else {
                double nx = x + dx / d * speed;
                double nz = z + dz / d * speed;
                float yaw = DetMath.yawDegrees(dx, dz);
                motion.set(i, nx, y, nz, yaw);
                frame.add(handle, nx, y, nz, yaw, STANDING);
            }
        }
    }

    /** Whether the last waypoint of {@code path} ends the move: the goal reached, or no way nearer. */
    private static boolean ends(PathStore.Waypoints path, PathStatus status) {
        return status == PathStatus.NO_PATH || (status == PathStatus.OK && !path.truncated());
    }
}
