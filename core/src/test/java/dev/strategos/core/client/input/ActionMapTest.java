package dev.strategos.core.client.input;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** UI-01.1: keys with their modifiers resolve to actions per context, control groups on the digits, and rebinding. */
class ActionMapTest {
    private final ActionMap map = ActionMap.defaults();

    @Test
    void keysResolvePerContext() {
        assertThat(map.resolve(ActionContext.RTS, KeyCode.key(KeyCode.W), Modifiers.NONE))
                .isEqualTo(Action.CAMERA_PAN_FORWARD);
        assertThat(map.resolve(ActionContext.IN_GAME, KeyCode.key(KeyCode.W), Modifiers.NONE))
                .as("W walks outside the RTS screen")
                .isNull();
        assertThat(map.resolve(ActionContext.IN_GAME, KeyCode.key(KeyCode.F6), Modifiers.NONE))
                .isEqualTo(Action.TOGGLE_RTS);
        assertThat(map.resolve(ActionContext.RTS, KeyCode.key(KeyCode.Z), Modifiers.SHIFT))
                .as("a modifier the binding does not need")
                .isEqualTo(Action.ATTACK_MOVE);
        assertThat(map.resolve(ActionContext.RTS, KeyCode.mouse(KeyCode.MOUSE_RIGHT), Modifiers.NONE))
                .isEqualTo(Action.ORDER_CONTEXT);
    }

    @Test
    void controlGroupsSitOnTheDigits() {
        assertThat(map.resolve(ActionContext.RTS, KeyCode.digit(3), Modifiers.CTRL))
                .isEqualTo(Action.CONTROL_GROUP_SET);
        assertThat(map.resolve(ActionContext.RTS, KeyCode.digit(3), Modifiers.SHIFT))
                .isEqualTo(Action.CONTROL_GROUP_ADD);
        assertThat(map.resolve(ActionContext.RTS, KeyCode.digit(0), Modifiers.NONE))
                .isEqualTo(Action.CONTROL_GROUP_SELECT);
        assertThat(map.resolve(ActionContext.FIRST_PERSON, KeyCode.digit(3), Modifiers.NONE))
                .as("the hotbar's in first person")
                .isNull();
    }

    @Test
    void aRebindingMovesTheAction() {
        ActionMap rebound = map.rebind(ActionContext.RTS, Action.ATTACK_MOVE, KeyCode.key('G'));

        assertThat(rebound.resolve(ActionContext.RTS, KeyCode.key('G'), Modifiers.NONE))
                .isEqualTo(Action.ATTACK_MOVE);
        assertThat(rebound.resolve(ActionContext.RTS, KeyCode.key(KeyCode.Z), Modifiers.NONE))
                .isNull();
        assertThat(rebound.keyOf(ActionContext.RTS, Action.ATTACK_MOVE)).isEqualTo(KeyCode.key('G'));
    }
}
