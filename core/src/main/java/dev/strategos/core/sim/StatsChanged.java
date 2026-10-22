package dev.strategos.core.sim;

import dev.strategos.core.sim.stats.StatScope;
import java.util.List;

/**
 * Modifiers changed some values (ADR-0013): a faction's ({@code id} its slot) or one unit's own ({@code id} its
 * handle); {@code paths}, sorted, are the value and token paths whose values may differ.
 */
public record StatsChanged(StatScope scope, int id, List<String> paths) implements BusMessage {
    public StatsChanged {
        paths = List.copyOf(paths);
    }
}
