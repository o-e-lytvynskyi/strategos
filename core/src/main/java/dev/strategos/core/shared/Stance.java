package dev.strategos.core.shared;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * How a unit fights without being told to (CMB-05, 04 §7.5): whom it engages, how far it chases, whether it returns
 * and whether it runs. The codes are the wire's and the templates' ({@code Behaviour/stance}); they only ever grow.
 */
@CoreApi
public enum Stance {
    VIOLENT(0),
    AGGRESSIVE(1),
    DEFENSIVE(2),
    STAND_GROUND(3),
    PASSIVE(4);

    private final int code;

    Stance(int code) {
        this.code = code;
    }

    /** The number the wire, the snapshot and the template path carry. */
    public int code() {
        return code;
    }

    /** The name a template or a save gives the stance: the lower-case snake form of the constant. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The stance of {@code code}, or null for a number no stance has. */
    public static @Nullable Stance of(int code) {
        return switch (code) { // no values() clone on the think path
            case 0 -> VIOLENT;
            case 1 -> AGGRESSIVE;
            case 2 -> DEFENSIVE;
            case 3 -> STAND_GROUND;
            case 4 -> PASSIVE;
            default -> null;
        };
    }

    /** The stance named {@code id}, or null for a name no stance has. */
    public static @Nullable Stance byId(String id) {
        for (Stance stance : values()) {
            if (stance.id().equals(id)) {
                return stance;
            }
        }
        return null;
    }
}
