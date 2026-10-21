package dev.strategos.core.testkit;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.port.Clock;
import dev.strategos.core.port.MetricsSink;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.CorePorts;
import dev.strategos.core.sim.InputEvent;
import dev.strategos.core.sim.MatchBounds;
import dev.strategos.core.sim.MatchConfig;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.TickInputs;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.UnitStateChanged;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs one match in plain JUnit, as the mod would: commands submitted during a tick enter the next tick's inputs. T5b's
 * first cut; T6b adds scenarios, invariants, fake bodies and replays.
 */
public final class SimHarness {
    private final MatchCore core;
    private final List<InputEvent> pending = new ArrayList<>();
    private final List<OutputEvent> events = new ArrayList<>();
    private int clientSeq;

    private SimHarness(MatchCore core) {
        this.core = core;
    }

    public static Builder builder() {
        return new Builder();
    }

    public MatchCore core() {
        return core;
    }

    public MatchQueries queries() {
        return core.queries();
    }

    /** Queues a command for the next tick, stamped with this tick and the harness's next sequence number. */
    public int submit(PlayerSlot issuer, Command command) {
        int seq = ++clientSeq;
        pending.add(new CommandEnvelope(issuer, seq, tick(), command));
        return seq;
    }

    /** An admin spawn of the set's first template, then one step; returns the new handles. */
    public int[] spawn(FactionSlot faction, Vec2d center, int count, double spread) {
        submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(core.queries().templates().byIndex(0).id(), faction, center, count, spread));
        TickOutputs out = step();
        return out.events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .toArray();
    }

    public TickOutputs step() {
        TickOutputs out = core.step(new TickInputs(tick() + 1, pending));
        pending.clear();
        events.addAll(out.events());
        return out;
    }

    public void runTicks(int n) {
        for (int i = 0; i < n; i++) {
            step();
        }
    }

    /** Every event of {@code type} since the harness was built. */
    public <E extends OutputEvent> List<E> events(Class<E> type) {
        return events.stream().filter(type::isInstance).map(type::cast).toList();
    }

    public List<CommandResult> results() {
        return events(CommandResult.class);
    }

    public List<UnitStateChanged> stateChanges() {
        return events(UnitStateChanged.class);
    }

    public long tick() {
        return core.queries().tick();
    }

    public long stateHash() {
        return core.stateHash();
    }

    /** A 32 x 32 match on a floor at y 1, seed 42, two factions, the built-in templates, unless told otherwise. */
    public static final class Builder {
        private MatchBounds bounds = new MatchBounds(32, 32, 0, 16, 1.0);
        private long seed = 42;
        private int factions = 2;
        private MatchRules rules = MatchRules.DEFAULTS;
        private TemplateSet templates = TemplateSet.builtin();
        private Clock clock = new FixedClock(0);
        private MetricsSink metrics = MetricsSink.none();
        private MatchId id = new MatchId(1);

        private Builder() {}

        public Builder size(int sizeX, int sizeZ) {
            bounds = new MatchBounds(sizeX, sizeZ, bounds.minY(), bounds.maxY(), bounds.flatFloorY());
            return this;
        }

        public Builder yBand(int minY, int maxY, double floorY) {
            bounds = new MatchBounds(bounds.sizeX(), bounds.sizeZ(), minY, maxY, floorY);
            return this;
        }

        public Builder bounds(MatchBounds bounds) {
            this.bounds = bounds;
            return this;
        }

        public Builder seed(long seed) {
            this.seed = seed;
            return this;
        }

        public Builder factions(int factions) {
            this.factions = factions;
            return this;
        }

        public Builder rules(MatchRules rules) {
            this.rules = rules;
            return this;
        }

        public Builder templates(TemplateSet templates) {
            this.templates = templates;
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder metrics(MetricsSink metrics) {
            this.metrics = metrics;
            return this;
        }

        public Builder id(MatchId id) {
            this.id = id;
            return this;
        }

        public SimHarness build() {
            MatchConfig config = new MatchConfig(id, seed, bounds, factions, rules);
            return new SimHarness(MatchCore.create(config, templates, new CorePorts(metrics, clock)));
        }
    }
}
