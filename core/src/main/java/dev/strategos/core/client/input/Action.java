package dev.strategos.core.client.input;

/**
 * The actions of the RTS controls (04 §13.3); the full hotkey set comes with UI-17. Held actions are polled each tick;
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
    /** The camera to the latest under-attack alert (UI-12). */
    JUMP_TO_ALERT(false),
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

    /** Polled each tick while its key is down, rather than fired once. */
    public boolean held() {
        return held;
    }
}
