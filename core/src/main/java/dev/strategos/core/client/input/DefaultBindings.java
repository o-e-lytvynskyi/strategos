package dev.strategos.core.client.input;

import java.util.List;

/**
 * The default bindings (UI-01, 04 §13.3), modelled on the classics where Minecraft leaves room. In the RTS screen the
 * camera pans on W, A, S and D, which vanilla binds only in game (they never conflict); it rotates on comma and period
 * ("<" and ">") and zooms on "=" and "-" and the wheel, because vanilla's Q and E bind drop and inventory in every
 * context. Attack-move is Z, beside A, where the classics put attack; stop is H (halt); formations are M (march in a box) and U (line; F, L, X and C are vanilla's), patrol O (P is vanilla's) and guard G; Enter sends a patrol; T is the all clear (back to work), semicolon garrisons, Backspace deletes (a second press confirms),
 * Shift + I is the next idle soldier, Ctrl + A selects the army and Ctrl + W the workers, F4, F5, F7 and F8 jump to the camera's
 * bookmarks and Ctrl with them sets one (UI-17); Shift on any order queues it (a modifier, not a binding); first person is V (view);
 * Space centres on the selection; B arms placement and cycles its buildings, R turns the ghost. Outside the screen, F6
 * enters RTS mode. The command card's fifteen cells (UI-08) are Alt and the classics' grid, Q W E R T / A S D F G /
 * Z X C V B: the grid alone would take the camera's and the actions' keys, so the card's bindings come first and a
 * held Alt sends them there. Fixed, not rebindable: the mouse (left
 * selects, a drag boxes, a double click takes all of a type, right orders, middle drags to rotate), Escape (cancel,
 * else pause) and the control groups on the digits (Ctrl sets, Shift adds, the digit selects, a double tap centres),
 * whose keys vanilla binds to the hotbar in every context (spec M2 §1.6). No default uses Ctrl + click, which macOS
 * turns into a right click.
 */
public final class DefaultBindings {
    private DefaultBindings() {}

    /** The grid's keys in reading order, the card cell each presses with Alt. */
    public static final String GRID = "QWERTASDFGZXCVB";

    public static List<Binding> all() {
        List<Binding> out = new java.util.ArrayList<>();
        for (Action a : Action.values()) {
            if (a.cardSlot() >= 0) { // first: a binding with Alt wins over the same key's plain one
                out.add(new Binding(a, ActionContext.RTS, KeyCode.key(GRID.charAt(a.cardSlot())), Modifiers.ALT, true));
            }
        }
        out.addAll(modified()); // before the plain keys they share
        out.addAll(plain());
        return List.copyOf(out);
    }

    /**
     * The keys that need Ctrl, Alt or Shift (UI-17), resolved before the plain keys they share (a binding needs at
     * least its own modifiers): idle soldiers beside the idle worker's I, Ctrl and the army's A or the workers' W,
     * bookmarks on F4, F5, F7 and F8 (jump) and with Ctrl (set). A key mapping takes one modifier, so none needs two.
     */
    private static List<Binding> modified() {
        List<Binding> out = new java.util.ArrayList<>();
        int[] marks = {KeyCode.F4, KeyCode.F5, KeyCode.F7, KeyCode.F8};
        for (int k = 0; k < marks.length; k++) {
            out.add(with(Action.valueOf("SET_BOOKMARK_" + (k + 1)), marks[k], Modifiers.CTRL));
        }
        for (int k = 0; k < marks.length; k++) {
            out.add(rts(Action.valueOf("JUMP_BOOKMARK_" + (k + 1)), marks[k]));
        }
        out.add(with(Action.NEXT_IDLE_SOLDIER, KeyCode.I, Modifiers.SHIFT));
        out.add(with(Action.SELECT_ALL_SOLDIERS, KeyCode.A, Modifiers.CTRL));
        out.add(with(Action.SELECT_ALL_WORKERS, KeyCode.W, Modifiers.CTRL));
        return out;
    }

    private static List<Binding> plain() {
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
                rts(Action.FORMATION_BOX, 'M'),
                rts(Action.FORMATION_LINE, 'U'),
                rts(Action.PATROL, 'O'),
                rts(Action.GUARD, KeyCode.G),
                rts(Action.BACK_TO_WORK, KeyCode.T),
                rts(Action.GARRISON, KeyCode.SEMICOLON),
                rts(Action.DELETE_SELECTED, KeyCode.BACKSPACE),
                rts(Action.CAPTURE, KeyCode.N),
                rts(Action.TOGGLE_FIRST_PERSON, KeyCode.V),
                rts(Action.BUILD, KeyCode.B),
                rts(Action.ROTATE, KeyCode.R),
                rts(Action.NEXT_IDLE_WORKER, KeyCode.I),
                rts(Action.JUMP_TO_ALERT, KeyCode.J),
                rts(Action.CONTROLS, KeyCode.K),
                rts(Action.TECH_TREE, KeyCode.Y),
                rts(Action.PAUSE_MATCH, KeyCode.F9),
                rts(Action.SLOWER, KeyCode.LEFT_BRACKET),
                rts(Action.FASTER, KeyCode.RIGHT_BRACKET),
                new Binding(Action.TOGGLE_RTS, ActionContext.IN_GAME, KeyCode.key(KeyCode.F6), Modifiers.NONE, true),
                new Binding(Action.ORDER_HERE, ActionContext.IN_GAME, KeyCode.key(KeyCode.G), Modifiers.NONE, true),
                new Binding(Action.FOLLOW_ME, ActionContext.IN_GAME, KeyCode.key(KeyCode.R), Modifiers.NONE, true),
                fixed(Action.SELECT, KeyCode.mouse(KeyCode.MOUSE_LEFT)),
                fixed(Action.ORDER_CONTEXT, KeyCode.mouse(KeyCode.MOUSE_RIGHT)),
                fixed(Action.CANCEL, KeyCode.key(KeyCode.ESCAPE)),
                fixed(Action.CONFIRM, KeyCode.key(KeyCode.ENTER)));
    }

    private static Binding rts(Action action, int key) {
        return new Binding(action, ActionContext.RTS, KeyCode.key(key), Modifiers.NONE, true);
    }

    private static Binding with(Action action, int key, int modifiers) {
        return new Binding(action, ActionContext.RTS, KeyCode.key(key), modifiers, true);
    }

    private static Binding fixed(Action action, KeyCode key) {
        return new Binding(action, ActionContext.RTS, key, Modifiers.NONE, false);
    }
}
