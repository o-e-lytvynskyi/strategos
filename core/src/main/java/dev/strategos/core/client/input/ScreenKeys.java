package dev.strategos.core.client.input;

/**
 * What the RTS screen does with a key before the action layer sees it (CAM-04.2, 04 §13.3): F1, F2, F3 and F11 go on
 * to their vanilla functions, which the open screen would otherwise swallow, and Escape cancels targeting or placement
 * before it pauses.
 */
public final class ScreenKeys {
    /** A key's route. */
    public enum Route {
        HIDE_GUI,
        SCREENSHOT,
        DEBUG_SCREEN,
        FULLSCREEN,
        CANCEL,
        PAUSE,
        ACTIONS
    }

    private ScreenKeys() {}

    public static Route route(int key, ActionContext context) {
        return switch (key) {
            case KeyCode.F1 -> Route.HIDE_GUI;
            case KeyCode.F2 -> Route.SCREENSHOT;
            case KeyCode.F3 -> Route.DEBUG_SCREEN;
            case KeyCode.F11 -> Route.FULLSCREEN;
            case KeyCode.ESCAPE -> context == ActionContext.RTS ? Route.PAUSE : Route.CANCEL;
            default -> Route.ACTIONS;
        };
    }
}
