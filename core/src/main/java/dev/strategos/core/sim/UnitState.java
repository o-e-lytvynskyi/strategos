package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;

/**
 * A unit's top-level state; M1's state machine keeps these. Stores, hashes and saves use {@link #code()}, never the
 * ordinal, so reordering the constants cannot change a hash or a save.
 */
@CoreApi
public enum UnitState {
    IDLE,
    WALKING;

    public int code() {
        return switch (this) {
            case IDLE -> 0;
            case WALKING -> 1;
        };
    }

    public static UnitState ofCode(int code) {
        return switch (code) {
            case 0 -> IDLE;
            case 1 -> WALKING;
            default -> throw new IllegalArgumentException("no unit state with code " + code);
        };
    }
}
