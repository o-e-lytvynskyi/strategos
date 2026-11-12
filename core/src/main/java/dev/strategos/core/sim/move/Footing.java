package dev.strategos.core.sim.move;

import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.nav.Surface;
import dev.strategos.core.sim.nav.SurfaceGrid;

/**
 * Where a walking body stands (PATH-05, S10): its height eases toward the floor under it, and that floor is the highest
 * one under its whole footprint within a step of its own column's. {@link MotionSystem} walks the columns; this places
 * the body on them.
 */
final class Footing {
    /** The most a body stands above its own column's floor, in sixteenths: one block, the step a walk climbs. */
    static final int STEP_UP = 16;

    private Footing() {}

    /**
     * Height toward the floor it stands on ({@link #standFloor}): halfway up a step on the first tick and onto it on
     * the second; down at most a half.
     */
    static void height(MatchState s, int i) {
        MotionStore motion = s.motion();
        if (motion.floor(i) == MotionStore.NO_FLOOR) {
            return;
        }
        height(s, i, standFloor(s, i));
    }

    /** The same per-tick easing with an already verified footprint floor. */
    static void height(MatchState s, int i, int floor) {
        MotionStore motion = s.motion();
        double floorY = s.config().bounds().minY() + floor / 16.0;
        double y = motion.y(i);
        if (Double.doubleToLongBits(floorY) == Double.doubleToLongBits(y) && !motion.rising(i)) {
            return; // no changed store values; preserve the original signed-zero normalization
        }
        if (floorY > y) {
            if (motion.rising(i)) {
                y = floorY;
                motion.setRising(i, false);
            } else {
                y = y + (floorY - y) / 2;
                motion.setRising(i, true);
            }
        } else {
            y = Math.max(floorY, y - MotionSystem.FALL);
            motion.setRising(i, false);
        }
        motion.set(i, motion.x(i), y, motion.z(i), motion.yaw(i));
    }

    /**
     * The floor a body stands on (S10, the owner's choice of 2026-10-23): the highest passable surface under its whole
     * footprint, as a vanilla body rests on the highest shape under its box, but at most {@value #STEP_UP} sixteenths
     * above its own column's floor, so a unit brushing a wall never stands on its top. The unit's own column stays its
     * floor for walking; only where the body stands changes, so a foot never cuts into a step's edge on the way up nor
     * leaves a ledge early on the way down.
     */
    static int standFloor(MatchState s, int i) {
        MotionStore motion = s.motion();
        int floor = motion.floor(i);
        double r = s.templates().byIndex(s.identity().templateIndex(i)).radius();
        int x0 = (int) Math.floor(motion.x(i) - r);
        int x1 = (int) Math.floor(motion.x(i) + r);
        int z0 = (int) Math.floor(motion.z(i) - r);
        int z1 = (int) Math.floor(motion.z(i) + r);
        SurfaceGrid grid = s.surfaces();
        int best = floor;
        for (int cz = z0; cz <= z1; cz++) {
            for (int cx = x0; cx <= x1; cx++) {
                int count = grid.count(cx, cz);
                for (int layer = 0; layer < count; layer++) {
                    int surface = grid.surface(cx, cz, layer);
                    int f = Surface.floor(surface);
                    if (Surface.passable(surface) && f > best && f <= floor + STEP_UP) {
                        best = f;
                    }
                }
            }
        }
        return best;
    }
}
