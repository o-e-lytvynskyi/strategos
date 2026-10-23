package dev.strategos.core.client.input;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Turns one screen's raw input into action events (04 §13.3), in GUI pixels and milliseconds: a left click selects, a
 * second within {@value #DOUBLE_CLICK_MS} ms and {@value #DRAG_PIXELS} pixels of the first takes every unit of that
 * type, a left drag of at least {@value #DRAG_PIXELS} pixels boxes; a middle drag rotates; a
 * control group's digit pressed twice within {@value #DOUBLE_CLICK_MS} ms centres on it; the right button previews the
 * order while held and issues it on release. Held actions are polled.
 */
public final class InputState {
    public static final long DOUBLE_CLICK_MS = 300;
    public static final double DRAG_PIXELS = 4;

    /** One action: where (and to where, for a box), the control group, and the modifiers it came with. */
    public record Event(Action action, double x, double y, double toX, double toY, int group, int modifiers) {
        static Event at(Action action, double x, double y, int modifiers) {
            return new Event(action, x, y, x, y, -1, modifiers);
        }
    }

    private final ActionMap map;
    private final Set<Action> held = EnumSet.noneOf(Action.class);
    private ActionContext context = ActionContext.RTS;
    private boolean leftDown;
    private boolean dragging;
    private double downX;
    private double downY;
    private long lastClick = Long.MIN_VALUE / 2;
    private double lastClickX;
    private double lastClickY;
    private boolean middleDown;
    private boolean rightDown;
    private double middleX;
    private double rotateDrag;
    private int lastGroup = -1;
    private long lastGroupAt = Long.MIN_VALUE / 2;

    public InputState(ActionMap map) {
        this.map = map;
    }

    public void context(ActionContext context) {
        this.context = context;
        held.clear();
    }

    public ActionContext context() {
        return context;
    }

    public List<Event> keyDown(KeyCode key, int mods, long now) {
        Action action = map.resolve(context, key, mods);
        List<Event> out = new ArrayList<>(1);
        if (action == null) {
            return out;
        }
        if (action.held()) {
            held.add(action);
            return out;
        }
        int group = key.digit();
        if (action == Action.CONTROL_GROUP_SELECT && group == lastGroup && now - lastGroupAt <= DOUBLE_CLICK_MS) {
            out.add(new Event(Action.CONTROL_GROUP_CENTRE, 0, 0, 0, 0, group, mods));
            lastGroup = -1;
            return out;
        }
        if (action == Action.CONTROL_GROUP_SELECT) {
            lastGroup = group;
            lastGroupAt = now;
        }
        out.add(new Event(action, 0, 0, 0, 0, group, mods));
        return out;
    }

    public void keyUp(KeyCode key, int mods) {
        Action action = map.resolve(context, key, mods);
        if (action == null) { // a modifier let go first: release whatever the key alone holds
            action = map.resolve(context, key, Modifiers.NONE);
        }
        if (action != null) {
            held.remove(action);
        }
    }

    public List<Event> mouseDown(int button, double x, double y, int mods, long now) {
        List<Event> out = new ArrayList<>(1);
        switch (button) {
            case KeyCode.MOUSE_LEFT -> {
                leftDown = true;
                dragging = false;
                downX = x;
                downY = y;
            }
            case KeyCode.MOUSE_RIGHT -> {
                rightDown = true;
                out.add(Event.at(Action.ORDER_PREVIEW, x, y, mods));
            }
            case KeyCode.MOUSE_MIDDLE -> {
                middleDown = true;
                middleX = x;
            }
            default -> {}
        }
        return out;
    }

    public void mouseMove(double x, double y) {
        if (leftDown && !dragging && Math.hypot(x - downX, y - downY) >= DRAG_PIXELS) {
            dragging = true;
        }
        if (middleDown) {
            rotateDrag += x - middleX;
            middleX = x;
        }
    }

    public List<Event> mouseUp(int button, double x, double y, int mods, long now) {
        List<Event> out = new ArrayList<>(1);
        if (button == KeyCode.MOUSE_MIDDLE) {
            middleDown = false;
        }
        if (button == KeyCode.MOUSE_RIGHT && rightDown) {
            rightDown = false;
            out.add(Event.at(Action.ORDER_CONTEXT, x, y, mods));
            return out;
        }
        if (button != KeyCode.MOUSE_LEFT || !leftDown) {
            return out;
        }
        leftDown = false;
        if (dragging || Math.hypot(x - downX, y - downY) >= DRAG_PIXELS) {
            out.add(new Event(Action.BOX_SELECT, downX, downY, x, y, -1, mods));
            dragging = false;
            return out;
        }
        if (now - lastClick <= DOUBLE_CLICK_MS && Math.hypot(x - lastClickX, y - lastClickY) < DRAG_PIXELS) {
            out.add(Event.at(Action.SELECT_ALL_OF_TYPE, x, y, mods));
            lastClick = Long.MIN_VALUE / 2;
            return out;
        }
        out.add(Event.at(Action.SELECT, x, y, mods));
        lastClick = now;
        lastClickX = x;
        lastClickY = y;
        return out;
    }

    /** Whether a held action's key is down. */
    public boolean held(Action action) {
        return held.contains(action);
    }

    /** GUI pixels the middle drag moved along X since the last call. */
    public double takeRotateDrag() {
        double drag = rotateDrag;
        rotateDrag = 0;
        return drag;
    }

    /** A left drag in progress, for the box's outline: its start, or an empty array. */
    public double[] dragFrom() {
        return dragging ? new double[] {downX, downY} : new double[0];
    }
}
