package dev.strategos.core.sim.order;

import org.jspecify.annotations.Nullable;

/**
 * What an order asks a unit to do, as the code its queue stores and the name a save uses. M1 has moves only; GATHER,
 * BUILD and ATTACK follow with their cards, each with a code from 2 on.
 */
public final class OrderKind {
    /** No order: an empty queue's current kind. */
    public static final int NONE = 0;

    public static final int MOVE = 1;

    private OrderKind() {}

    /** The name a save gives an order of {@code kind}. */
    public static String name(int kind) {
        return switch (kind) {
            case MOVE -> "move";
            default -> throw new IllegalArgumentException("no order kind " + kind);
        };
    }

    /** The kind a save names {@code name}, or null for any other name. */
    public static @Nullable Integer of(String name) {
        return switch (name) {
            case "move" -> MOVE;
            default -> null;
        };
    }
}
