package dev.strategos.core.sim;

import java.util.ArrayList;
import java.util.List;

/** The nine phases of a step, in their fixed order (04 §4.2); empty phases cost one loop iteration. */
public enum Phase {
    INGEST,
    COMMANDS,
    AI,
    UNIT_AI,
    MOVEMENT,
    COMBAT,
    ECONOMY,
    WORLD,
    FINAL;

    /** The names in order, for {@code PhaseTimers}. */
    public static List<String> names() {
        List<String> names = new ArrayList<>();
        for (Phase phase : values()) {
            names.add(phase.name());
        }
        return List.copyOf(names);
    }
}
