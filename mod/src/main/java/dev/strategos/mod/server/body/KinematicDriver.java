package dev.strategos.mod.server.body;

import dev.strategos.core.shared.HotPath;
import dev.strategos.core.sim.BodyFrame;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.server.match.MatchSpace;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Writes a step's poses onto the bodies, in world coordinates, before the tracker ships them in the same tick. A body
 * never stands where the level does not tick entities (04 §1.4 row 1): a pose that would put it there retires it, so
 * it cannot drop into a chunk that is not loaded, nor freeze beyond the simulation distance.
 */
@HotPath
public final class KinematicDriver {
    private KinematicDriver() {}

    public static void apply(ServerLevel level, MatchSpace space, BodyRegistry bodies, BodyFrame frame) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int i = 0; i < frame.count(); i++) {
            int handle = frame.handle(i);
            UnitEntity body = bodies.get(handle);
            if (body == null) {
                continue;
            }
            double x = space.worldX(frame.x(i));
            double y = frame.y(i);
            double z = space.worldZ(frame.z(i));
            if (level.isPositionEntityTicking(at.set(x, y, z))) {
                body.applyPose(x, y, z, frame.yaw(i));
            } else {
                bodies.retire(handle);
            }
        }
    }
}
