package dev.strategos.core.sim.nav;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import dev.strategos.core.shared.CoreApi;
import java.util.Locale;

/**
 * How a path request ended (04 §9.3). Stores and hashes use {@link #code()}, saves the lower-case name, never the
 * ordinal.
 */
@CoreApi
public enum PathStatus {
    /** The path reaches the goal. */
    OK,
    /** The search hit its expansion cap: the path ends at the node nearest the goal, where the unit asks again. */
    PARTIAL,
    /**
     * The goal cannot be reached: the start has no surface, or its region does not hold the goal. The path, possibly
     * empty, ends as near the goal as the region gets, and the move is over there.
     */
    NO_PATH,
    /** Too many requests were waiting; the unit asks again later (admission, PATH-16). */
    BUSY;

    public static final Codec<PathStatus> CODEC = Codec.STRING.comapFlatMap(PathStatus::read, PathStatus::id);

    public int code() {
        return switch (this) {
            case OK -> 0;
            case PARTIAL -> 1;
            case NO_PATH -> 2;
            case BUSY -> 3;
        };
    }

    public static PathStatus ofCode(int code) {
        return switch (code) {
            case 0 -> OK;
            case 1 -> PARTIAL;
            case 2 -> NO_PATH;
            case 3 -> BUSY;
            default -> throw new IllegalArgumentException("no path status with code " + code);
        };
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    private static DataResult<PathStatus> read(String id) {
        for (PathStatus status : values()) {
            if (status.id().equals(id)) {
                return DataResult.success(status);
            }
        }
        return DataResult.error(() -> "unknown path status '" + id + "'");
    }
}
