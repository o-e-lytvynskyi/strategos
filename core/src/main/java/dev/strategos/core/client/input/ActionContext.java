package dev.strategos.core.client.input;

/**
 * Where a binding is live (04 §13.3): {@code RTS} while the RTS screen owns input, {@code PLACEMENT} and {@code
 * TARGETING} while it places a building or picks a target, {@code FIRST_PERSON} in first person, {@code IN_GAME} in
 * plain play. Like NeoForge's conflict contexts, a binding of one context conflicts only with a binding of a context
 * that is live at the same time: the RTS screen's three share their screen; vanilla's {@code UNIVERSAL} bindings
 * conflict with every context, its {@code IN_GAME} ones only with {@code IN_GAME} and {@code FIRST_PERSON}, and its
 * {@code GUI} ones with none of ours, since the RTS screen reads its keys itself.
 */
public enum ActionContext {
    RTS,
    PLACEMENT,
    TARGETING,
    FIRST_PERSON,
    IN_GAME;

    /** Whether a binding here and one of vanilla's context {@code vanilla} would be live together. */
    public boolean conflictsWithVanilla(String vanilla) {
        return switch (vanilla) {
            case "UNIVERSAL" -> true;
            case "IN_GAME" -> this == IN_GAME || this == FIRST_PERSON;
            case "GUI" -> false;
            default -> throw new IllegalArgumentException("no vanilla conflict context " + vanilla);
        };
    }

    /** Whether the RTS screen owns input in this context. */
    public boolean onRtsScreen() {
        return this == RTS || this == PLACEMENT || this == TARGETING;
    }
}
