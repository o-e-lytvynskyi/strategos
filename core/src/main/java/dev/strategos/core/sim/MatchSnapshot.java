package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;
import java.util.List;

/**
 * A match at a completed tick: everything a restore needs. The schema version is not a field: the snapshot codec writes
 * and migrates {@code "v"} (M0 spec 5.8).
 */
@CoreApi
public record MatchSnapshot(
        MatchConfig config,
        long tick,
        long templateSetHash,
        EntitySlots slots,
        List<UnitRecord> units,
        List<CommandEnvelope> pendingCommands) {
    public MatchSnapshot {
        units = List.copyOf(units);
        pendingCommands = List.copyOf(pendingCommands);
    }
}
