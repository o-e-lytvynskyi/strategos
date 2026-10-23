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
    ORDER_CONTEXT(false),
    ATTACK_MOVE(false),
    STOP(false),
    CONTROL_GROUP_SET(false),
    CONTROL_GROUP_SELECT(false),
    CONTROL_GROUP_ADD(false),
    CONTROL_GROUP_CENTRE(false),
    TOGGLE_FIRST_PERSON(false),
    TOGGLE_RTS(false),
    CANCEL(false);

    private final boolean held;

    Action(boolean held) {
        this.held = held;
    }

    /** Polled each tick while its key is down, rather than fired once. */
    public boolean held() {
        return held;
    }
}
