package dev.strategos.core.sim.unitai;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.sim.UnitState;

/**
 * A unit's activity tier (UNIT-05, 04 §6.5): what it is doing, never who watches it. ACTIVE fights or was ordered
 * within {@value #ORDERED} ticks, MOVING walks, WORKING gathers or builds, IDLE stands with no order. The architecture's
 * think periods are the table's; M4 keeps each leaf's own period, which already prices an idle unit at a sixth of a
 * walking one (M4 §1.6 row 6), so a tier reports and does not slow. Codes keep M1's overlay: idle 0, moving 1.
 */
@CoreApi
public enum ActivityTier {
    IDLE,
    MOVING,
    WORKING,
    ACTIVE;

    /** Ticks an order keeps a unit ACTIVE. */
    public static final long ORDERED = 40;

    public int code() {
        return switch (this) {
            case IDLE -> 0;
            case MOVING -> 1;
            case WORKING -> 2;
            case ACTIVE -> 3;
        };
    }

    /** The architecture's think period of the tier, in ticks (04 §6.5's table). */
    public int period() {
        return switch (this) {
            case IDLE -> 20;
            case MOVING -> 4;
            case WORKING -> 10;
            case ACTIVE -> 2;
        };
    }

    /** The tier of a unit in {@code state}, ordered within {@value #ORDERED} ticks or not. */
    public static ActivityTier of(UnitState state, boolean ordered) {
        if (state == UnitState.COMBAT || ordered) {
            return ACTIVE;
        }
        return switch (state) {
            case WALKING -> MOVING;
            case GATHERING, BUILDING -> WORKING;
            default -> IDLE;
        };
    }
}
