package dev.strategos.core.client.input;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** UI-01.1: clicks, double clicks within 300 ms, drags of at least 4 GUI pixels, held keys and double taps. */
class GestureTest {
    private final InputState input = new InputState(ActionMap.defaults());

    @Test
    void aClickSelectsAndASecondWithin300MsTakesTheType() {
        assertThat(click(100, 50, 50, 0)).extracting(InputState.Event::action).containsExactly(Action.SELECT);
        assertThat(click(101, 51, 50, 300))
                .extracting(InputState.Event::action)
                .containsExactly(Action.SELECT_ALL_OF_TYPE);
        assertThat(click(100, 50, 1_000, 1_301))
                .as("301 ms is two clicks")
                .extracting(InputState.Event::action)
                .containsExactly(Action.SELECT);
        assertThat(click(100, 50, 2_000, 2_301))
                .extracting(InputState.Event::action)
                .containsExactly(Action.SELECT);
    }

    @Test
    void aDragOfFourPixelsBoxes() {
        input.mouseDown(KeyCode.MOUSE_LEFT, 10, 10, Modifiers.NONE, 0);
        input.mouseMove(13.9, 10);
        assertThat(input.mouseUp(KeyCode.MOUSE_LEFT, 13.9, 10, Modifiers.NONE, 50))
                .extracting(InputState.Event::action)
                .containsExactly(Action.SELECT);

        input.mouseDown(KeyCode.MOUSE_LEFT, 10, 10, Modifiers.SHIFT, 1_000);
        input.mouseMove(14, 10);
        input.mouseMove(80, 60);
        List<InputState.Event> box = input.mouseUp(KeyCode.MOUSE_LEFT, 80, 60, Modifiers.SHIFT, 1_100);
        assertThat(box).hasSize(1);
        assertThat(box.get(0).action()).isEqualTo(Action.BOX_SELECT);
        assertThat(box.get(0))
                .extracting(InputState.Event::x, InputState.Event::y, InputState.Event::toX)
                .containsExactly(10.0, 10.0, 80.0);
        assertThat(Modifiers.shift(box.get(0).modifiers()))
                .as("Shift adds to the selection")
                .isTrue();
    }

    @Test
    void theRightButtonPreviewsThenOrdersAndAMiddleDragRotates() {
        assertThat(input.mouseDown(KeyCode.MOUSE_RIGHT, 5, 5, Modifiers.SHIFT, 0))
                .extracting(InputState.Event::action)
                .containsExactly(Action.ORDER_PREVIEW);
        assertThat(input.mouseUp(KeyCode.MOUSE_RIGHT, 9, 5, Modifiers.SHIFT, 400))
                .as("released where the order goes, held as long as it likes")
                .extracting(InputState.Event::action, InputState.Event::x, e -> Modifiers.shift(e.modifiers()))
                .containsExactly(org.assertj.core.groups.Tuple.tuple(Action.ORDER_CONTEXT, 9.0, true));
        input.mouseDown(KeyCode.MOUSE_MIDDLE, 100, 0, Modifiers.NONE, 0);
        input.mouseMove(130, 0);
        input.mouseMove(125, 0);
        assertThat(input.takeRotateDrag()).isEqualTo(25.0);
        assertThat(input.takeRotateDrag()).isZero();
    }

    @Test
    void heldKeysArePolledAndGroupsCentreOnADoubleTap() {
        input.keyDown(KeyCode.key(KeyCode.W), Modifiers.NONE, 0);
        assertThat(input.held(Action.CAMERA_PAN_FORWARD)).isTrue();
        input.keyUp(KeyCode.key(KeyCode.W), Modifiers.NONE);
        assertThat(input.held(Action.CAMERA_PAN_FORWARD)).isFalse();

        assertThat(input.keyDown(KeyCode.digit(2), Modifiers.NONE, 1_000))
                .extracting(InputState.Event::action, InputState.Event::group)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(Action.CONTROL_GROUP_SELECT, 2));
        assertThat(input.keyDown(KeyCode.digit(2), Modifiers.NONE, 1_250))
                .extracting(InputState.Event::action)
                .containsExactly(Action.CONTROL_GROUP_CENTRE);
        assertThat(input.keyDown(KeyCode.digit(2), Modifiers.CTRL, 1_300))
                .extracting(InputState.Event::action)
                .containsExactly(Action.CONTROL_GROUP_SET);
    }

    private List<InputState.Event> click(double x, double y, long down, long up) {
        input.mouseDown(KeyCode.MOUSE_LEFT, x, y, Modifiers.NONE, down);
        return input.mouseUp(KeyCode.MOUSE_LEFT, x, y, Modifiers.NONE, up);
    }
}
