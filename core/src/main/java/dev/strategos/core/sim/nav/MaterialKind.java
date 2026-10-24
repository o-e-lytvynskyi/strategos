package dev.strategos.core.sim.nav;

import dev.strategos.core.shared.CoreApi;

/**
 * What a block is to the economy (ECO-02, the {@code strategos:resource_yield} data map): a tree's log or leaves, rock,
 * ore, a forage plant, a crop, and a spawned deposit's node (ECO-08). Codes, never ordinals, go into slices.
 */
@CoreApi
public enum MaterialKind {
    LOG,
    LEAVES,
    STONE,
    ORE,
    FORAGE,
    CROP,
    /** A mine or a quarry: a spawned deposit, never a block's own kind (ECO-08). */
    DEPOSIT;

    public int code() {
        return switch (this) {
            case LOG -> 1;
            case LEAVES -> 2;
            case STONE -> 3;
            case ORE -> 4;
            case FORAGE -> 5;
            case CROP -> 6;
            case DEPOSIT -> 7;
        };
    }

    public static MaterialKind ofCode(int code) {
        return switch (code) {
            case 1 -> LOG;
            case 2 -> LEAVES;
            case 3 -> STONE;
            case 4 -> ORE;
            case 5 -> FORAGE;
            case 6 -> CROP;
            case 7 -> DEPOSIT;
            default -> throw new IllegalArgumentException("no material kind with code " + code);
        };
    }

    /** The name a data map entry uses: {@code "log"}, {@code "leaves"}, and so on. */
    public String key() {
        return switch (this) {
            case LOG -> "log";
            case LEAVES -> "leaves";
            case STONE -> "stone";
            case ORE -> "ore";
            case FORAGE -> "forage";
            case CROP -> "crop";
            case DEPOSIT -> "deposit";
        };
    }
}
