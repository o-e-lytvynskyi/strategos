package dev.strategos.core.sim.order;

import org.jspecify.annotations.Nullable;

/**
 * What an order asks a unit to do, as the code its queue stores and the name a save uses. M1 has moves; M2 adds the
 * player's attack on a unit and attack-move to a place (ORD-04), and the engagement a unit takes up itself: on an
 * enemy it noticed idle or walking and fighting, or on one that hit it (CMB-02). GATHER and BUILD follow with their
 * cards.
 */
public final class OrderKind {
    /** No order: an empty queue's current kind. */
    public static final int NONE = 0;

    public static final int MOVE = 1;
    /** The player's attack on a unit: chased until it dies or the order ends. */
    public static final int ATTACK = 2;
    /** Walking to a place, fighting the enemies met on the way. */
    public static final int ATTACK_MOVE = 3;
    /** An attack the unit took up itself, put before its orders: given up when the target leaves its sight. */
    public static final int ENGAGE = 4;

    private OrderKind() {}

    /** The name a save gives an order of {@code kind}. */
    public static String name(int kind) {
        return switch (kind) {
            case MOVE -> "move";
            case ATTACK -> "attack";
            case ATTACK_MOVE -> "attack_move";
            case ENGAGE -> "engage";
            default -> throw new IllegalArgumentException("no order kind " + kind);
        };
    }

    /** The kind a save names {@code name}, or null for any other name. */
    public static @Nullable Integer of(String name) {
        return switch (name) {
            case "move" -> MOVE;
            case "attack" -> ATTACK;
            case "attack_move" -> ATTACK_MOVE;
            case "engage" -> ENGAGE;
            default -> null;
        };
    }

    /** Whether an order of {@code kind} attacks a unit, which its queue holds as the order's target. */
    public static boolean attacks(int kind) {
        return kind == ATTACK || kind == ENGAGE;
    }
}
