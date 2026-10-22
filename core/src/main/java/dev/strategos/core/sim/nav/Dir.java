package dev.strategos.core.sim.nav;

import dev.strategos.core.shared.CoreApi;

/** The eight neighbour directions on the XZ plane, north (-Z) first, clockwise; even ones are orthogonal. */
@CoreApi
public final class Dir {
    public static final int N = 0;
    public static final int NE = 1;
    public static final int E = 2;
    public static final int SE = 3;
    public static final int S = 4;
    public static final int SW = 5;
    public static final int W = 6;
    public static final int NW = 7;
    public static final int COUNT = 8;

    private Dir() {}

    public static int dx(int dir) {
        return switch (dir) {
            case NE, E, SE -> 1;
            case SW, W, NW -> -1;
            default -> 0;
        };
    }

    public static int dz(int dir) {
        return switch (dir) {
            case NW, N, NE -> -1;
            case SE, S, SW -> 1;
            default -> 0;
        };
    }

    public static boolean diagonal(int dir) {
        return (dir & 1) == 1;
    }

    /** The orthogonal direction one step anticlockwise of a diagonal (NE → N). */
    public static int left(int dir) {
        return (dir + 7) & 7;
    }

    /** The orthogonal direction one step clockwise of a diagonal (NE → E). */
    public static int right(int dir) {
        return (dir + 1) & 7;
    }

    public static int opposite(int dir) {
        return (dir + 4) & 7;
    }
}
