package dev.strategos.core.sim.nav;

import dev.strategos.core.shared.CoreApi;

/**
 * One standable surface packed in an {@code int} (the candidate layout S8 checks, 04 §9.1): bits 0–10 the floor in
 * 1/16 above the band's bottom, 11–17 the headroom in 1/16 (capped at 127), 18–20 the kind, 21–23 the cost code (the
 * blocked orthogonal neighbours), 24–31 one link bit per direction of {@link Dir}.
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

    public static final int MAX_FLOOR = 0x7FF;
    public static final int MAX_HEADROOM = 0x7F;

    private Surface() {}

    public static int pack(int floor, int headroom, int kind, int cost, int links) {
        if (floor < 0 || floor > MAX_FLOOR) {
            throw new IllegalArgumentException("floor " + floor + " outside 0.." + MAX_FLOOR);
        }
        return floor
                | Math.min(headroom, MAX_HEADROOM) << 11
                | (kind & 0x7) << 18
                | Math.min(cost, 7) << 21
                | (links & 0xFF) << 24;
    }

    public static int floor(int s) {
        return s & MAX_FLOOR;
    }

    public static int headroom(int s) {
        return (s >>> 11) & MAX_HEADROOM;
    }

    /** The floor plus the headroom: the ceiling's height in 1/16. */
    public static int ceiling(int s) {
        return floor(s) + headroom(s);
    }

    public static int kind(int s) {
        return (s >>> 18) & 0x7;
    }

    public static int cost(int s) {
        return (s >>> 21) & 0x7;
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
        return (s & ~(0x7 << 21)) | Math.min(cost, 7) << 21;
    }
}
