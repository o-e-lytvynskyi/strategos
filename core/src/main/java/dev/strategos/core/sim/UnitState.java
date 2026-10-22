package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;
import java.util.Locale;

/**
 * A unit's top-level state: the activity the leaf of its machine reports ({@code unitai.StateId}), which views, the
 * trace events and the movement systems read. Stores use {@link #code()}, never the ordinal, so reordering the
 * constants cannot change a hash; saves name the machine's leaf instead.
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

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
