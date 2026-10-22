package dev.strategos.core.sim.nav;

import dev.strategos.core.shared.CoreApi;

/** A page's position in a match's grid: columns {@code 16x .. 16x+15} and {@code 16z .. 16z+15}, match-local. */
@CoreApi
public record PageCoord(int x, int z) {
    public PageCoord {
        if (x < 0 || z < 0) {
            throw new IllegalArgumentException("page " + x + ", " + z + " is outside the match");
        }
    }

    /** The page holding the match-local column. */
    public static PageCoord of(int localX, int localZ) {
        return new PageCoord(localX >> 4, localZ >> 4);
    }
}
