package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.combat.ShotStore;
import dev.strategos.core.sim.diplomacy.DiplomacyState;
import dev.strategos.core.sim.econ.CarryStore;
import dev.strategos.core.sim.econ.NodeStore;
import dev.strategos.core.sim.garrison.GarrisonStore;
import dev.strategos.core.sim.nav.PathStore;
import dev.strategos.core.sim.order.StanceStore;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A match at a completed tick: everything a restore needs, its terrain pages as references to the slices they were
 * built from, path requests in flight with their results computed, stable template bindings (schema 3),
 * and immutable diplomacy authority (schema 4).
 * The schema version is not a field: the snapshot codec writes and migrates {@code "v"} (M0 spec 5.8).
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
        List<CarryStore.Saved> carried,
        Play play,
        List<TemplateBinding> templateBindings) {
    /**
     * How the match is played (MATCH-01, AI-01): the outcome as {@code Outcome} saves it, the computer players, and
     * which of them think early.
     */
    @CoreApi
    public record Play(
            List<Long> outcome,
            List<AiSlots.Saved> ai,
            long aiWake,
            List<Long> alerts,
            ShotStore.Snapshot shots,
            List<StanceStore.Saved> stances,
            List<GarrisonStore.Saved> garrisons,
            @Nullable DiplomacyState diplomacy) {
        // AFTER-M6: preserve diplomacy when composing the outcome/statistics snapshot additions.
        public static final Play EMPTY =
                new Play(List.of(), List.of(), 0, List.of(), ShotStore.Snapshot.EMPTY, List.of(), List.of());

        /** Compatibility construction before schema 4; the enclosing snapshot supplies explicit identity. */
        public Play(
                List<Long> outcome,
                List<AiSlots.Saved> ai,
                long aiWake,
                List<Long> alerts,
                ShotStore.Snapshot shots,
                List<StanceStore.Saved> stances,
                List<GarrisonStore.Saved> garrisons) {
            this(outcome, ai, aiWake, alerts, shots, stances, garrisons, null);
        }

        public Play withDiplomacy(DiplomacyState saved) {
            return new Play(outcome, ai, aiWake, alerts, shots, stances, garrisons, saved);
        }

        /** A play without garrisons, as before BLD-16 saved them. */
        public Play(
                List<Long> outcome,
                List<AiSlots.Saved> ai,
                long aiWake,
                List<Long> alerts,
                ShotStore.Snapshot shots,
                List<StanceStore.Saved> stances) {
            this(outcome, ai, aiWake, alerts, shots, stances, List.of());
        }

        /** A play without stances, as before CMB-05 saved them. */
        public Play(
                List<Long> outcome, List<AiSlots.Saved> ai, long aiWake, List<Long> alerts, ShotStore.Snapshot shots) {
            this(outcome, ai, aiWake, alerts, shots, List.of(), List.of());
        }

        public Play(List<Long> outcome, List<AiSlots.Saved> ai, long aiWake, List<Long> alerts) {
            this(outcome, ai, aiWake, alerts, ShotStore.Snapshot.EMPTY, List.of(), List.of());
        }

        /** A play with no live alert, as before MATCH-04 saved them. */
        public Play(List<Long> outcome, List<AiSlots.Saved> ai, long aiWake) {
            this(outcome, ai, aiWake, List.of(), ShotStore.Snapshot.EMPTY, List.of(), List.of());
        }

        /** Whether the persisted victory outcome is terminal, even if the registry record is older. */
        public boolean ended() {
            Outcome restored = new Outcome();
            restored.restore(outcome);
            return restored.ended();
        }

        public Play {
            outcome = List.copyOf(outcome);
            ai = List.copyOf(ai);
            alerts = List.copyOf(alerts);
            stances = List.copyOf(stances);
            garrisons = List.copyOf(garrisons);
        }
    }

    /** Saved session indices, resolved by stable id on restore; absent in legacy snapshots. */
    @CoreApi
    public record TemplateBinding(int index, TemplateId id) {}

    /** Authority is non-null on every enclosing snapshot, including compatibility constructors. */
    public DiplomacyState diplomacy() {
        return java.util.Objects.requireNonNull(play.diplomacy());
    }

    public MatchSnapshot {
        var savedDiplomacy = play.diplomacy();
        if (savedDiplomacy == null) {
            savedDiplomacy = DiplomacyState.identity(config.factions());
            play = play.withDiplomacy(savedDiplomacy);
        }
        if (savedDiplomacy.factions() != config.factions()) {
            throw new IllegalArgumentException("saved diplomacy faction count differs from match config");
        }
        templateBindings = List.copyOf(templateBindings);
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

    /** Snapshot without a persisted session table; live building references require bindings before restore. */
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
            List<BuildingStore.Saved> buildings,
            List<Long> ledger,
            List<CarryStore.Saved> carried,
            Play play) {
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
                ledger,
                carried,
                play,
                List.of());
    }

    /** A snapshot from before victory and computer players (MATCH-01, AI-01). */
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
            List<BuildingStore.Saved> buildings,
            List<Long> ledger,
            List<CarryStore.Saved> carried) {
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
                ledger,
                carried,
                Play.EMPTY);
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
