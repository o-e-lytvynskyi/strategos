package dev.strategos.core.sim.nav;

import dev.strategos.core.shared.CoreApi;

/**
 * One standable surface packed in an {@code int} (04 §9.1, the layout S8 fixed): bits 0–11 the floor in 1/16 above
 * the band's bottom (a full block on the band's top voxel is 2,048, so 11 bits were one short), 12–18 the headroom in
 * 1/16 (capped at 127), 19–21 the kind, 22–23 the cost code (the blocked orthogonal neighbours, capped at 3), 24–31 one
 * link bit per direction of {@link Dir}.
 */
@CoreApi
public final class Surface {
    public static final int GROUND = 0;
    public static final int SHALLOW_WATER = 1;

    /** Two or more blocks of water above the floor: impassable for infantry. */
    public static final int DEEP_WATER = 2;

    public static final int DOOR = 3;
    public static final int SLOW = 4;

    /** Impassable. */
    public static final int HAZARD = 5;

    public static final int MAX_FLOOR = 0xFFF;
    public static final int MAX_HEADROOM = 0x7F;
    public static final int MAX_COST = 3;

    private Surface() {}

    public static int pack(int floor, int headroom, int kind, int cost, int links) {
        if (floor < 0 || floor > MAX_FLOOR) {
            throw new IllegalArgumentException("floor " + floor + " outside 0.." + MAX_FLOOR);
        }
        return floor
                | Math.min(headroom, MAX_HEADROOM) << 12
                | (kind & 0x7) << 19
                | Math.min(cost, MAX_COST) << 22
                | (links & 0xFF) << 24;
    }

    public static int floor(int s) {
        return s & MAX_FLOOR;
    }

    public static int headroom(int s) {
        return (s >>> 12) & MAX_HEADROOM;
    }

    /** The floor plus the headroom: the ceiling's height in 1/16. */
    public static int ceiling(int s) {
        return floor(s) + headroom(s);
    }

    public static int kind(int s) {
        return (s >>> 19) & 0x7;
    }

    public static int cost(int s) {
        return (s >>> 22) & MAX_COST;
    }

    public static int links(int s) {
        return s >>> 24;
    }

    public static boolean linked(int s, int dir) {
        return (links(s) & (1 << dir)) != 0;
    }

    public static boolean passable(int s) {
        int kind = kind(s);
        return kind != DEEP_WATER && kind != HAZARD;
    }

    static int withLinks(int s, int links) {
        return (s & 0x00FF_FFFF) | (links & 0xFF) << 24;
    }

    static int withCost(int s, int cost) {
        return (s & ~(MAX_COST << 22)) | Math.min(cost, MAX_COST) << 22;
    }
}
