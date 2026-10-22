package dev.strategos.core.sim.world;

import dev.strategos.core.shared.CoreApi;

/** The order of writes inside a site (04 §11.7): node removals, construction, damage stages, cosmetics, map import. */
@CoreApi
public enum WritePriority {
    NODE_REMOVAL,
    CONSTRUCTION,
    DAMAGE_STAGE,
    COSMETIC,
    IMPORT;

    public static final int COUNT = 5;

    /** 0 for the first served; the same as the declaration order, written out so no code reads an ordinal. */
    public int rank() {
        return switch (this) {
            case NODE_REMOVAL -> 0;
            case CONSTRUCTION -> 1;
            case DAMAGE_STAGE -> 2;
            case COSMETIC -> 3;
            case IMPORT -> 4;
        };
    }
}
