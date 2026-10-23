package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.econ.CarryStore;
import dev.strategos.core.sim.econ.NodeStore;
import dev.strategos.core.sim.nav.PathStore;
import java.util.List;

/**
 * A match at a completed tick: everything a restore needs, its terrain pages as references to the slices they were
 * built from, and the path requests in flight with their results computed (schema 2). The schema version is not a
 * field: the snapshot codec writes and migrates {@code "v"} (M0 spec 5.8).
 */
@CoreApi
public record MatchSnapshot(
        MatchConfig config,
        long tick,
        long templateSetHash,
        EntitySlots slots,
        List<UnitRecord> units,
        List<CommandEnvelope> pendingCommands,
        List<PageRef> pages,
        PathStore.Queue paths,
        int nextGroup,
        List<Long> treasury,
        List<Long> terrainEdits,
        List<NodeStore.Saved> nodes,
        List<BuildingStore.Saved> buildings,
        List<Long> ledger,
        List<CarryStore.Saved> carried) {
    public MatchSnapshot {
        buildings = List.copyOf(buildings);
        ledger = List.copyOf(ledger);
        carried = List.copyOf(carried);
        units = List.copyOf(units);
        treasury = List.copyOf(treasury);
        terrainEdits = List.copyOf(terrainEdits);
        nodes = List.copyOf(nodes);
        pendingCommands = List.copyOf(pendingCommands);
        pages = List.copyOf(pages);
    }

    /** A snapshot from before gathering (ECO-05): no ledger, no loads. */
    public MatchSnapshot(
            MatchConfig config,
            long tick,
            long templateSetHash,
            EntitySlots slots,
            List<UnitRecord> units,
            List<CommandEnvelope> pendingCommands,
            List<PageRef> pages,
            PathStore.Queue paths,
            int nextGroup,
            List<Long> treasury,
            List<Long> terrainEdits,
            List<NodeStore.Saved> nodes,
            List<BuildingStore.Saved> buildings) {
        this(
                config,
                tick,
                templateSetHash,
                slots,
                units,
                pendingCommands,
                pages,
                paths,
                nextGroup,
                treasury,
                terrainEdits,
                nodes,
                buildings,
                List.of(),
                List.of());
    }

    /** A snapshot from before buildings (BLD-01). */
    public MatchSnapshot(
            MatchConfig config,
            long tick,
            long templateSetHash,
            EntitySlots slots,
            List<UnitRecord> units,
            List<CommandEnvelope> pendingCommands,
            List<PageRef> pages,
            PathStore.Queue paths,
            int nextGroup,
            List<Long> treasury,
            List<Long> terrainEdits,
            List<NodeStore.Saved> nodes) {
        this(
                config,
                tick,
                templateSetHash,
                slots,
                units,
                pendingCommands,
                pages,
                paths,
                nextGroup,
                treasury,
                terrainEdits,
                nodes,
                List.of());
    }

    /** A snapshot from before resource nodes (ECO-02). */
    public MatchSnapshot(
            MatchConfig config,
            long tick,
            long templateSetHash,
            EntitySlots slots,
            List<UnitRecord> units,
            List<CommandEnvelope> pendingCommands,
            List<PageRef> pages,
            PathStore.Queue paths,
            int nextGroup,
            List<Long> treasury,
            List<Long> terrainEdits) {
        this(
                config,
                tick,
                templateSetHash,
                slots,
                units,
                pendingCommands,
                pages,
                paths,
                nextGroup,
                treasury,
                terrainEdits,
                List.of());
    }

    /** A snapshot from before the core edited terrain (M3 T5). */
    public MatchSnapshot(
            MatchConfig config,
            long tick,
            long templateSetHash,
            EntitySlots slots,
            List<UnitRecord> units,
            List<CommandEnvelope> pendingCommands,
            List<PageRef> pages,
            PathStore.Queue paths,
            int nextGroup,
            List<Long> treasury) {
        this(
                config,
                tick,
                templateSetHash,
                slots,
                units,
                pendingCommands,
                pages,
                paths,
                nextGroup,
                treasury,
                List.of());
    }

    /** A snapshot from before the economy (ECO-01): no treasury. */
    public MatchSnapshot(
            MatchConfig config,
            long tick,
            long templateSetHash,
            EntitySlots slots,
            List<UnitRecord> units,
            List<CommandEnvelope> pendingCommands,
            List<PageRef> pages,
            PathStore.Queue paths,
            int nextGroup) {
        this(config, tick, templateSetHash, slots, units, pendingCommands, pages, paths, nextGroup, List.of());
    }

    /** A snapshot from before group orders (ORD-02): the next group id is the first. */
    public MatchSnapshot(
            MatchConfig config,
            long tick,
            long templateSetHash,
            EntitySlots slots,
            List<UnitRecord> units,
            List<CommandEnvelope> pendingCommands,
            List<PageRef> pages,
            PathStore.Queue paths) {
        this(config, tick, templateSetHash, slots, units, pendingCommands, pages, paths, MatchState.FIRST_GROUP);
    }

    /** A snapshot with no path request in flight. */
    public MatchSnapshot(
            MatchConfig config,
            long tick,
            long templateSetHash,
            EntitySlots slots,
            List<UnitRecord> units,
            List<CommandEnvelope> pendingCommands,
            List<PageRef> pages) {
        this(config, tick, templateSetHash, slots, units, pendingCommands, pages, PathStore.Queue.EMPTY);
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
