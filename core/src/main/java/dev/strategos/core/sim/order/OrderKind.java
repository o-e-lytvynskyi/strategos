package dev.strategos.core.sim.order;

import org.jspecify.annotations.Nullable;

/**
 * What an order asks a unit to do, as the code its queue stores and the name a save uses. M1 has moves; M2 adds the
 * player's attack on a unit and attack-move to a place (ORD-04), the engagement a unit takes up itself: on an enemy it
 * noticed idle or walking and fighting, or on one that hit it (CMB-02), and following a commander in first person
 * (CAM-07); M3 adds fleeing from a blow (UNIT-04), gathering at a resource node (ECO-05) and building (BLD-03).
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
    /** Keeping a place behind a commander in first person (CAM-07), whose player slot the queue holds as the target. */
    public static final int FOLLOW = 5;
    /** Running from a blow (UNIT-04), put before the unit's orders, so it goes back to them once it is away. */
    public static final int FLEE = 6;
    /** Gathering at a resource node (ECO-05), whose id the queue holds as the target: carried back, then gathered again. */
    public static final int GATHER = 7;
    /** Building a foundation or a site (BLD-03), whose handle the queue holds as the target. */
    public static final int BUILD = 8;
    /** Burning a building with a torch (CMB-03), whose handle the queue holds as the target. */
    public static final int BURN = 9;

    private OrderKind() {}

    /** The name a save gives an order of {@code kind}. */
    public static String name(int kind) {
        return switch (kind) {
            case MOVE -> "move";
            case ATTACK -> "attack";
            case ATTACK_MOVE -> "attack_move";
            case ENGAGE -> "engage";
            case FOLLOW -> "follow";
            case FLEE -> "flee";
            case GATHER -> "gather";
            case BUILD -> "build";
            case BURN -> "burn";
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
            case "follow" -> FOLLOW;
            case "flee" -> FLEE;
            case "gather" -> GATHER;
            case "build" -> BUILD;
            case "burn" -> BURN;
            default -> null;
        };
    }

    /** Whether an order of {@code kind} attacks a unit, which its queue holds as the order's target. */
    public static boolean attacks(int kind) {
        return kind == ATTACK || kind == ENGAGE;
    }

    /**
     * Whether an order of {@code kind} holds a target: the unit it attacks, the player slot it follows, the node it
     * gathers at, or the building it builds or burns.
     */
    public static boolean targets(int kind) {
        return attacks(kind) || kind == FOLLOW || kind == GATHER || kind == BUILD || kind == BURN;
    }
}
