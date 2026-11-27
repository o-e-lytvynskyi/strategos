package dev.strategos.core.client.input;

/**
 * The actions of the RTS controls (04 §13.3); the full hotkey set is UI-17's. Held actions are polled each tick;
 * the others are events.
 */
public enum Action {
    CAMERA_PAN_FORWARD(true),
    CAMERA_PAN_BACK(true),
    CAMERA_PAN_LEFT(true),
    CAMERA_PAN_RIGHT(true),
    CAMERA_ROTATE_LEFT(true),
    CAMERA_ROTATE_RIGHT(true),
    CAMERA_ZOOM_IN(true),
    CAMERA_ZOOM_OUT(true),
    CAMERA_CENTRE_ON_SELECTION(false),
    SELECT(false),
    BOX_SELECT(false),
    SELECT_ALL_OF_TYPE(false),
    ORDER_PREVIEW(false),
    ORDER_CONTEXT(false),
    ATTACK_MOVE(false),
    STOP(false),
    /** The selection's next ground click marches it in a box formation (ORD-06). */
    FORMATION_BOX(false),
    /** The selection's next ground click marches it in a line formation (ORD-06). */
    FORMATION_LINE(false),
    /** The selection's next ground clicks, Shift-click for each point but the last, patrol between the points (ORD-07). */
    PATROL(false),
    /** The selection's next click on a friendly unit or building guards it (ORD-07). */
    GUARD(false),
    /** Enter: ends a patrol's points and sends it (ORD-07). */
    CONFIRM(false),
    /** Held while right-clicking an enemy building: the selection captures it instead of attacking it (CMB-10). */
    CAPTURE(true),
    CONTROL_GROUP_SET(false),
    CONTROL_GROUP_SELECT(false),
    CONTROL_GROUP_ADD(false),
    CONTROL_GROUP_CENTRE(false),
    TOGGLE_FIRST_PERSON(false),
    TOGGLE_RTS(false),
    /** In first person: the selection moves to where the player looks, or attacks the enemy there (CAM-05, CAM-07). */
    ORDER_HERE(false),
    /** In first person: the selection follows the player (CAM-07). */
    FOLLOW_ME(false),
    CANCEL(false),
    /** Placement mode (BLD-02): the next building template to place, after the last none, until the command card. */
    BUILD(false),
    /** Placement mode: the ghost turns a quarter. */
    ROTATE(false),
    /** The next idle worker, in a stable order: selected, the camera on it (UI-10). */
    NEXT_IDLE_WORKER(false),
    /** The next idle soldier, as the idle worker's key does: selected, the camera on it (UI-17). */
    NEXT_IDLE_SOLDIER(false),
    /** Every own soldier selected (UI-17). */
    SELECT_ALL_SOLDIERS(false),
    /** Every own worker selected (UI-17). */
    SELECT_ALL_WORKERS(false),
    /** The all clear: the bell's workers go back to work, at the selected building or at every holder (UI-17). */
    BACK_TO_WORK(false),
    /** The selected units into the nearest own building that takes them (UI-17). */
    GARRISON(false),
    /** Delete the selected units, or demolish the selected building; asks for a second press (UI-17). */
    DELETE_SELECTED(false),
    /** The camera's place kept in bookmark 1 to 4 (UI-17); the session's, not saved. */
    SET_BOOKMARK_1(false),
    SET_BOOKMARK_2(false),
    SET_BOOKMARK_3(false),
    SET_BOOKMARK_4(false),
    /** The camera to bookmark 1 to 4 (UI-17). */
    JUMP_BOOKMARK_1(false),
    JUMP_BOOKMARK_2(false),
    JUMP_BOOKMARK_3(false),
    JUMP_BOOKMARK_4(false),
    /** The camera to the latest under-attack alert (UI-12). */
    JUMP_TO_ALERT(false),
    /** The controls overlay (UI-14): the mod's keys on their current keys, rebinds included. */
    CONTROLS(false),
    /** The technology tree screen (TECH-05): the phases, the lines, what is researched and what stands in the way. */
    TECH_TREE(false),
    /** Singleplayer: the match paused, or resumed (MATCH-05). */
    PAUSE_MATCH(false),
    /** Singleplayer: the next slower game speed offered. */
    SLOWER(false),
    /** Singleplayer: the next faster game speed offered. */
    FASTER(false),
    /** The command card's cells (UI-08), in reading order: Alt and the grid's key, Q W E R T / A S D F G / Z X C V B. */
    CARD_1(false),
    CARD_2(false),
    CARD_3(false),
    CARD_4(false),
    CARD_5(false),
    CARD_6(false),
    CARD_7(false),
    CARD_8(false),
    CARD_9(false),
    CARD_10(false),
    CARD_11(false),
    CARD_12(false),
    CARD_13(false),
    CARD_14(false),
    CARD_15(false);

    private final boolean held;

    Action(boolean held) {
        this.held = held;
    }

    /** The command card cell this action presses (UI-08), 0 to 14, or -1 for any other action. */
    public int cardSlot() {
        return switch (this) {
            case CARD_1 -> 0;
            case CARD_2 -> 1;
            case CARD_3 -> 2;
            case CARD_4 -> 3;
            case CARD_5 -> 4;
            case CARD_6 -> 5;
            case CARD_7 -> 6;
            case CARD_8 -> 7;
            case CARD_9 -> 8;
            case CARD_10 -> 9;
            case CARD_11 -> 10;
            case CARD_12 -> 11;
            case CARD_13 -> 12;
            case CARD_14 -> 13;
            case CARD_15 -> 14;
            default -> -1;
        };
    }

    /** The bookmark (0 to 3) this action sets, or -1 for any other action. */
    public int setsBookmark() {
        return switch (this) {
            case SET_BOOKMARK_1 -> 0;
            case SET_BOOKMARK_2 -> 1;
            case SET_BOOKMARK_3 -> 2;
            case SET_BOOKMARK_4 -> 3;
            default -> -1;
        };
    }

    /** The bookmark (0 to 3) this action jumps to, or -1 for any other action. */
    public int jumpsToBookmark() {
        return switch (this) {
            case JUMP_BOOKMARK_1 -> 0;
            case JUMP_BOOKMARK_2 -> 1;
            case JUMP_BOOKMARK_3 -> 2;
            case JUMP_BOOKMARK_4 -> 3;
            default -> -1;
        };
    }

    /** Polled each tick while its key is down, rather than fired once. */
    public boolean held() {
        return held;
    }
}
