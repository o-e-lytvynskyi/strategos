package dev.strategos.core.client.input;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Resolves a key with its modifiers to an action in a context: the bindings first, then, in the RTS contexts, the
 * control-group digits (Ctrl sets, Shift adds, the bare digit selects). A binding needs at least its own modifiers.
 */
public final class ActionMap {
    private final List<Binding> bindings;

    public ActionMap(List<Binding> bindings) {
        this.bindings = List.copyOf(bindings);
    }

    public static ActionMap defaults() {
        return new ActionMap(DefaultBindings.all());
    }

    /** The action {@code key} with {@code mods} fires in {@code context}, or null. */
    public @Nullable Action resolve(ActionContext context, KeyCode key, int mods) {
        for (Binding b : bindings) {
            if (b.context() == context && b.key().equals(key) && (mods & b.modifiers()) == b.modifiers()) {
                return b.action();
            }
        }
        if (context.onRtsScreen() && key.digit() >= 0) {
            if (Modifiers.ctrl(mods)) {
                return Action.CONTROL_GROUP_SET;
            }
            return Modifiers.shift(mods) ? Action.CONTROL_GROUP_ADD : Action.CONTROL_GROUP_SELECT;
        }
        return null;
    }

    /** The key {@code action} is bound to in {@code context}, or null. */
    public @Nullable KeyCode keyOf(ActionContext context, Action action) {
        for (Binding b : bindings) {
            if (b.context() == context && b.action() == action) {
                return b.key();
            }
        }
        return null;
    }

    /** This map with {@code action}'s binding in {@code context} moved to {@code key}, as a player's rebinding does. */
    public ActionMap rebind(ActionContext context, Action action, KeyCode key) {
        List<Binding> next = bindings.stream()
                .map(b -> b.context() == context && b.action() == action
                        ? new Binding(b.action(), b.context(), key, b.modifiers(), b.rebindable())
                        : b)
                .toList();
        return new ActionMap(next);
    }

    public List<Binding> bindings() {
        return bindings;
    }
}
