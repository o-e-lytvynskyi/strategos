package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;
import java.util.List;

/**
 * A match at a completed tick: everything a restore needs, its terrain pages as references to the slices they were
 * built from (schema 2). The schema version is not a field: the snapshot codec writes and migrates {@code "v"} (M0
 * spec 5.8).
 */
@CoreApi
public record MatchSnapshot(
        MatchConfig config,
        long tick,
        long templateSetHash,
        EntitySlots slots,
        List<UnitRecord> units,
        List<CommandEnvelope> pendingCommands,
        List<PageRef> pages) {
    public MatchSnapshot {
        units = List.copyOf(units);
        pendingCommands = List.copyOf(pendingCommands);
        pages = List.copyOf(pages);
    }

    /** A snapshot without terrain pages. */
    public MatchSnapshot(
            MatchConfig config,
            long tick,
            long templateSetHash,
            EntitySlots slots,
            List<UnitRecord> units,
            List<CommandEnvelope> pendingCommands) {
        this(config, tick, templateSetHash, slots, units, pendingCommands, List.of());
    }
}
