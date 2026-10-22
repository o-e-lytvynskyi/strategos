package dev.strategos.core.sim;

import dev.strategos.core.sim.stats.StatScope;
import dev.strategos.core.sim.unitai.AiMessage;
import java.util.List;

/**
 * Modifiers changed some values (ADR-0013): a faction's ({@code id} its slot) or one unit's own ({@code id} its
 * handle); {@code paths}, sorted, are the value and token paths whose values may differ. It names a scope, not a unit,
 * so it reaches units' machines only once a state handles it (none does in M1).
 */
public record StatsChanged(StatScope scope, int id, List<String> paths) implements BusMessage, AiMessage {
    public StatsChanged {
        paths = List.copyOf(paths);
    }
}
