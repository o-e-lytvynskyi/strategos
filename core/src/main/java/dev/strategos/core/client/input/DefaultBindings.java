package dev.strategos.core.client.input;

import java.util.List;

/**
 * The default bindings (UI-01, 04 §13.3), modelled on the classics where Minecraft leaves room. In the RTS screen the
 * camera pans on W, A, S and D, which vanilla binds only in game (they never conflict); it rotates on comma and period
 * ("<" and ">") and zooms on "=" and "-" and the wheel, because vanilla's Q and E bind drop and inventory in every
 * context. Attack-move is Z, beside A, where the classics put attack; stop is H (halt); first person is V (view);
 * Space centres on the selection. Outside the screen, F6 enters RTS mode. Fixed, not rebindable: the mouse (left
 * selects, a drag boxes, a double click takes all of a type, right orders, middle drags to rotate), Escape (cancel,
 * else pause) and the control groups on the digits (Ctrl sets, Shift adds, the digit selects, a double tap centres),
 * whose keys vanilla binds to the hotbar in every context (spec M2 §1.6). No default uses Ctrl + click, which macOS
 * turns into a right click.
 */
public final class DefaultBindings {
    private DefaultBindings() {}

    public static List<Binding> all() {
        return List.of(
                rts(Action.CAMERA_PAN_FORWARD, KeyCode.W),
                rts(Action.CAMERA_PAN_BACK, KeyCode.S),
                rts(Action.CAMERA_PAN_LEFT, KeyCode.A),
                rts(Action.CAMERA_PAN_RIGHT, KeyCode.D),
                rts(Action.CAMERA_ROTATE_LEFT, KeyCode.COMMA),
                rts(Action.CAMERA_ROTATE_RIGHT, KeyCode.PERIOD),
                rts(Action.CAMERA_ZOOM_IN, KeyCode.EQUAL),
                rts(Action.CAMERA_ZOOM_OUT, KeyCode.MINUS),
                rts(Action.CAMERA_CENTRE_ON_SELECTION, KeyCode.SPACE),
                rts(Action.ATTACK_MOVE, KeyCode.Z),
                rts(Action.STOP, KeyCode.H),
                rts(Action.TOGGLE_FIRST_PERSON, KeyCode.V),
                new Binding(Action.TOGGLE_RTS, ActionContext.IN_GAME, KeyCode.key(KeyCode.F6), Modifiers.NONE, true),
                new Binding(Action.ORDER_HERE, ActionContext.IN_GAME, KeyCode.key(KeyCode.G), Modifiers.NONE, true),
                fixed(Action.SELECT, KeyCode.mouse(KeyCode.MOUSE_LEFT)),
                fixed(Action.ORDER_CONTEXT, KeyCode.mouse(KeyCode.MOUSE_RIGHT)),
                fixed(Action.CANCEL, KeyCode.key(KeyCode.ESCAPE)));
    }

    private static Binding rts(Action action, int key) {
        return new Binding(action, ActionContext.RTS, KeyCode.key(key), Modifiers.NONE, true);
    }

    private static Binding fixed(Action action, KeyCode key) {
        return new Binding(action, ActionContext.RTS, key, Modifiers.NONE, false);
    }
}
