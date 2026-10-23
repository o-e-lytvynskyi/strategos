package dev.strategos.core.client.input;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** CAM-04.2: F1, F2, F3 and F11 are forwarded, and Escape cancels targeting or placement before it pauses. */
class ScreenKeysTest {
    @Test
    void theFunctionKeysAreForwarded() {
        assertThat(ScreenKeys.route(KeyCode.F1, ActionContext.RTS)).isEqualTo(ScreenKeys.Route.HIDE_GUI);
        assertThat(ScreenKeys.route(KeyCode.F2, ActionContext.RTS)).isEqualTo(ScreenKeys.Route.SCREENSHOT);
        assertThat(ScreenKeys.route(KeyCode.F3, ActionContext.TARGETING)).isEqualTo(ScreenKeys.Route.DEBUG_SCREEN);
        assertThat(ScreenKeys.route(KeyCode.F11, ActionContext.PLACEMENT)).isEqualTo(ScreenKeys.Route.FULLSCREEN);
        assertThat(ScreenKeys.route(KeyCode.W, ActionContext.RTS)).isEqualTo(ScreenKeys.Route.ACTIONS);
    }

    @Test
    void escapeCancelsBeforeItPauses() {
        assertThat(ScreenKeys.route(KeyCode.ESCAPE, ActionContext.TARGETING)).isEqualTo(ScreenKeys.Route.CANCEL);
        assertThat(ScreenKeys.route(KeyCode.ESCAPE, ActionContext.PLACEMENT)).isEqualTo(ScreenKeys.Route.CANCEL);
        assertThat(ScreenKeys.route(KeyCode.ESCAPE, ActionContext.RTS)).isEqualTo(ScreenKeys.Route.PAUSE);
    }
}
