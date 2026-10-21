package dev.strategos.core.sim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import dev.strategos.core.shared.CoreApi;
import java.util.Locale;

/**
 * A unit's top-level state; M1's state machine keeps these. Stores and hashes use {@link #code()}, saves the lower-case
 * name, never the ordinal, so reordering the constants cannot change a hash or a save.
 */
@CoreApi
public enum UnitState {
    IDLE,
    WALKING;

    public static final Codec<UnitState> CODEC = Codec.STRING.comapFlatMap(UnitState::read, UnitState::id);

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

    private static DataResult<UnitState> read(String id) {
        for (UnitState state : values()) {
            if (state.id().equals(id)) {
                return DataResult.success(state);
            }
        }
        return DataResult.error(() -> "unknown unit state '" + id + "'");
    }
}
