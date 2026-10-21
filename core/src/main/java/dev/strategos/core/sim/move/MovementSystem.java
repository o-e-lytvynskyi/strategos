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

/**
 * P4: every walking unit with a move order steps {@code walkSpeed} toward its target, and lands exactly on it once it is
 * within one step, so a move takes ⌈distance / speed⌉ steps. This is the one place movement reads the template's speed (M1's stat service replaces the read).
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
        TemplateSet templates = state.templates();
        BodyFrame.Builder frame = state.frame();
        int highWater = entities.highWater();
        for (int i = 0; i < highWater; i++) {
            if (!entities.aliveAt(i) || state.ai().state(i) != UnitState.WALKING || orders.kind(i) != OrderStore.MOVE) {
                continue;
            }
            double speed = templates.byIndex(identity.templateIndex(i)).walkSpeed();
            double x = motion.x(i);
            double y = motion.y(i);
            double z = motion.z(i);
            double tx = orders.targetX(i);
            double tz = orders.targetZ(i);
            double dx = tx - x;
            double dz = tz - z;
            double d = DetMath.length(dx, dz);
            int handle = entities.handleAt(i);
            if (d <= speed + SNAP_TOLERANCE) {
                float yaw = d > 0 ? DetMath.yawDegrees(dx, dz) : motion.yaw(i);
                motion.set(i, tx, y, tz, yaw);
                frame.add(handle, tx, y, tz, yaw, STANDING);
                state.bus().publish(new Arrived(handle));
            } else {
                double nx = x + dx / d * speed;
                double nz = z + dz / d * speed;
                float yaw = DetMath.yawDegrees(dx, dz);
                motion.set(i, nx, y, nz, yaw);
                frame.add(handle, nx, y, nz, yaw, STANDING);
            }
        }
    }
}
