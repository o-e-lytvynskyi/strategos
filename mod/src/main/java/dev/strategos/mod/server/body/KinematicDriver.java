package dev.strategos.mod.server.body;

import dev.strategos.core.shared.HotPath;
import dev.strategos.core.sim.BodyFrame;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.server.match.MatchSpace;

/** Writes a step's poses onto the bodies, in world coordinates, before the tracker ships them in the same tick. */
@HotPath
public final class KinematicDriver {
    private KinematicDriver() {}

    public static void apply(MatchSpace space, BodyRegistry bodies, BodyFrame frame) {
        for (int i = 0; i < frame.count(); i++) {
            UnitEntity body = bodies.get(frame.handle(i));
            if (body != null) {
                body.applyPose(space.worldX(frame.x(i)), frame.y(i), space.worldZ(frame.z(i)), frame.yaw(i));
            }
        }
    }
}
