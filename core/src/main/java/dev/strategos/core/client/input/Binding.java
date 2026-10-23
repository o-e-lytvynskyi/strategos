package dev.strategos.core.client.input;

import java.util.Locale;

/**
 * A key or mouse button with the modifiers it needs, bound to an action in a context; {@code rebindable} when it is
 * registered as a vanilla {@code KeyMapping}, so players rebind it in the Controls screen. Mouse gestures and the
 * control-group digits are fixed (spec M2 §1.6).
 */
public record Binding(Action action, ActionContext context, KeyCode key, int modifiers, boolean rebindable) {
    /** The translation key of its {@code KeyMapping}: {@code key.strategos.<action in lower case>}. */
    public String mappingName() {
        return "key.strategos." + action.name().toLowerCase(Locale.ROOT);
    }
}
