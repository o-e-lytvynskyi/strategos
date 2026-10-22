package dev.strategos.core.sim.nav;

import dev.strategos.core.shared.CoreApi;

/**
 * What navigation needs to know of a block state, in a {@code short} (04 §9.2): bits 0–4 hold the collision top in
 * 1/16 block (0 none, 16 a full block, 24 for fences and walls), the rest are the flags below. The adapter builds the
 * table of every state once; pure code only reads it.
 */
@CoreApi
public final class BlockClass {
    public static final int TOP_MASK = 0x1F;

    /** The top can carry a unit: false for fences, walls, leaves, climbables and hazards. */
    public static final int STANDABLE = 1 << 5;

    /** No collision: air, plants, torches, fluids, fire. */
    public static final int PASSABLE = 1 << 6;

    public static final int WATER = 1 << 7;

    /** Lava, which is also a hazard. */
    public static final int LAVA = 1 << 8;

    /** Ladders, vines and scaffolding: clear for headroom, never a floor; units do not climb. */
    public static final int CLIMBABLE = 1 << 9;

    /** Doors and fence gates: passable, at a cost. */
    public static final int DOOR = 1 << 10;

    /** Lava, fire, magma, cactus, sweet berry bushes, powder snow: never entered. */
    public static final int HAZARD = 1 << 11;

    /** Leaves: an obstacle, never a floor. */
    public static final int LEAVES = 1 << 12;

    /** Soul sand, honey, cobwebs: slower and dearer. */
    public static final int SLOW = 1 << 13;

    /** The adapter classifies these with their real position; cleared in slices. */
    public static final int POSITION_DEPENDENT = 1 << 14;

    public static final short AIR = (short) PASSABLE;

    /** A full solid block. */
    public static final short SOLID = (short) (16 | STANDABLE);

    private BlockClass() {}

    /** A class from a collision top (0–24 sixteenths) and flags. */
    public static short of(int top, int flags) {
        if (top < 0 || top > 24) {
            throw new IllegalArgumentException("collision top " + top + " outside 0..24 sixteenths");
        }
        if ((flags & TOP_MASK) != 0 || (flags & ~0xFFFF) != 0) {
            throw new IllegalArgumentException("flags overlap the top bits: " + Integer.toHexString(flags));
        }
        return (short) (top | flags);
    }

    public static int top(short c) {
        return c & TOP_MASK;
    }

    public static boolean is(short c, int flag) {
        return (c & flag) != 0;
    }

    /** A voxel that leaves its height clear for a unit: no collision, a door, a climbable or fluid. */
    public static boolean clear(short c) {
        return (c & (PASSABLE | DOOR | CLIMBABLE)) != 0;
    }

    /** The class as a slice stores it, without the adapter-only flag. */
    public static short forSlice(short c) {
        return (short) (c & ~POSITION_DEPENDENT);
    }
}
