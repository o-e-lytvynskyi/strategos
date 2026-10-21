package dev.strategos.core.testkit;

import dev.strategos.core.sim.BodyFrame;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.UnitCreated;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/** The mod's bodies, faked: a unit's {@code UnitCreated} places one, and every body frame moves it. */
public final class FakeBodies {
    private final Map<Integer, Pose> poses = new TreeMap<>();

    /** Applies one tick's outputs: the created units first, then the frame's poses. */
    public void accept(TickOutputs out) {
        for (OutputEvent event : out.events()) {
            if (event instanceof UnitCreated created) {
                place(created.handle(), created.x(), created.y(), created.z(), created.yaw(), out.tick());
            }
        }
        BodyFrame frame = out.bodies();
        for (int i = 0; i < frame.count(); i++) {
            place(frame.handle(i), frame.x(i), frame.y(i), frame.z(i), frame.yaw(i), out.tick());
        }
    }

    /** The last pose of the body of {@code handle}, or null when there is none. */
    public @Nullable Pose pose(int handle) {
        return poses.get(handle);
    }

    public int count() {
        return poses.size();
    }

    /** A body that exists without a {@code UnitCreated}: a restored match's units. */
    void place(int handle, double x, double y, double z, float yaw, long tick) {
        poses.put(handle, new Pose(x, y, z, yaw, tick));
    }

    /** Where a body stands and the tick it got there. */
    public record Pose(double x, double y, double z, float yaw, long tick) {}
}
