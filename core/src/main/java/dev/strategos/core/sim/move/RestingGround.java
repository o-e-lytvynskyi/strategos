package dev.strategos.core.sim.move;

import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MotionStore;

/** Derived ground geometry for idle settling; exact cell keys also cover small avoidance steps. */
final class RestingGround {
    private boolean[] grounded = new boolean[0];
    private boolean[] standing = new boolean[0];
    private long[] groundVersions = new long[0];
    private long[] standVersions = new long[0];
    private int[] groundX = new int[0];
    private int[] groundZ = new int[0];
    private int[] groundFloors = new int[0];
    private int[] standFloors = new int[0];
    private int[] targetFloors = new int[0];
    private int[] minX = new int[0];
    private int[] maxX = new int[0];
    private int[] minZ = new int[0];
    private int[] maxZ = new int[0];

    void ensure(int capacity) {
        if (capacity <= grounded.length) {
            return;
        }
        grounded = new boolean[capacity];
        standing = new boolean[capacity];
        groundVersions = new long[capacity];
        standVersions = new long[capacity];
        groundX = new int[capacity];
        groundZ = new int[capacity];
        groundFloors = new int[capacity];
        standFloors = new int[capacity];
        targetFloors = new int[capacity];
        minX = new int[capacity];
        maxX = new int[capacity];
        minZ = new int[capacity];
        maxZ = new int[capacity];
    }

    boolean ground(MatchState s, int i) {
        MotionStore motion = s.motion();
        long version = s.surfaces().version();
        int x = (int) Math.floor(motion.x(i));
        int z = (int) Math.floor(motion.z(i));
        if (grounded[i]
                && groundVersions[i] == version
                && groundX[i] == x
                && groundZ[i] == z
                && groundFloors[i] == motion.floor(i)) {
            return true;
        }
        grounded[i] = MotionSystem.ground(s, i);
        if (grounded[i]) {
            groundVersions[i] = version;
            groundX[i] = x;
            groundZ[i] = z;
            groundFloors[i] = motion.floor(i);
        } else {
            standing[i] = false;
        }
        return grounded[i];
    }

    void height(MatchState s, int i) {
        MotionStore motion = s.motion();
        int floor = motion.floor(i);
        double radius = s.templates().byIndex(s.identity().templateIndex(i)).radius();
        int x0 = (int) Math.floor(motion.x(i) - radius);
        int x1 = (int) Math.floor(motion.x(i) + radius);
        int z0 = (int) Math.floor(motion.z(i) - radius);
        int z1 = (int) Math.floor(motion.z(i) + radius);
        long version = s.surfaces().version();
        if (!standing[i]
                || standVersions[i] != version
                || standFloors[i] != floor
                || minX[i] != x0
                || maxX[i] != x1
                || minZ[i] != z0
                || maxZ[i] != z1) {
            targetFloors[i] = Footing.standFloor(s, i);
            standing[i] = true;
            standVersions[i] = version;
            standFloors[i] = floor;
            minX[i] = x0;
            maxX[i] = x1;
            minZ[i] = z0;
            maxZ[i] = z1;
        }
        Footing.height(s, i, targetFloors[i]);
    }
}
