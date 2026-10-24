package dev.strategos.core.testkit;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.port.Clock;
import dev.strategos.core.port.JobExecutor;
import dev.strategos.core.port.JournalSink;
import dev.strategos.core.port.MetricsSink;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.Agent;
import dev.strategos.core.sim.AgentUpdate;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.CorePorts;
import dev.strategos.core.sim.ExternalDamage;
import dev.strategos.core.sim.InputEvent;
import dev.strategos.core.sim.JobDelivery;
import dev.strategos.core.sim.MatchBounds;
import dev.strategos.core.sim.MatchConfig;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.SliceSource;
import dev.strategos.core.sim.TerrainPage;
import dev.strategos.core.sim.TickInputs;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.UnitRecord;
import dev.strategos.core.sim.UnitStateChanged;
import dev.strategos.core.sim.journal.ReplayDriver;
import dev.strategos.core.sim.journal.ReplayResult;
import dev.strategos.core.sim.nav.DeliveryMode;
import dev.strategos.core.sim.nav.MobilityClass;
import dev.strategos.core.sim.nav.PageBuilder;
import dev.strategos.core.sim.nav.PageCoord;
import dev.strategos.core.sim.nav.SurfaceGrid;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;
import java.util.function.LongFunction;
import org.jspecify.annotations.Nullable;

/**
 * Runs one match in plain JUnit, as the mod would: commands submitted during a tick enter the next tick's inputs. Every
 * step applies its outputs to {@link FakeBodies} and checks this harness's {@link Invariants}; the first broken one
 * throws {@link InvariantViolation}, so the invariants hold after every tick of every harness run (QA-02.3).
 */
public final class SimHarness implements ScenarioContext {
    /** The seed of a harness built without one (M0 spec 7.10). */
    public static final long DEFAULT_SEED = 42;

    private final MatchCore core;
    private final long seed;
    private final Invariants invariants = new Invariants();
    private final FakeBodies bodies = new FakeBodies();
    private final List<InputEvent> pending = new ArrayList<>();
    private @Nullable LongFunction<List<Agent>> agents;
    private final TreeMap<Long, List<InputEvent>> scheduled = new TreeMap<>();
    private final List<OutputEvent> events = new ArrayList<>();
    private List<String> violations = List.of();
    private int clientSeq;

    private SimHarness(MatchCore core) {
        this.core = core;
        this.seed = core.queries().config().seed();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * A harness continuing {@code snapshot} on a fixed clock; its bodies start where the snapshot's units stand, and
     * its client sequence numbers start again from 1.
     */
    public static SimHarness restore(MatchSnapshot snapshot, TemplateSet templates) {
        return restore(snapshot, templates, SliceSource.NONE);
    }

    /** As {@link #restore(MatchSnapshot, TemplateSet)}, the snapshot's terrain pages rebuilt from {@code slices}. */
    public static SimHarness restore(MatchSnapshot snapshot, TemplateSet templates, SliceSource slices) {
        SimHarness h = new SimHarness(MatchCore.restore(snapshot, templates, CorePorts.of(new FixedClock(0)), slices));
        for (UnitRecord unit : snapshot.units()) {
            h.bodies.place(unit.handle(), unit.x(), unit.y(), unit.z(), unit.yaw(), snapshot.tick());
        }
        return h;
    }

    /** A harness of the scenario's size and seed, the builder's defaults otherwise. */
    public static SimHarness forScenario(Scenario scenario) {
        return forScenario(scenario, JobExecutor.inline());
    }

    /** As {@link #forScenario(Scenario)}, its path and page jobs run by {@code jobs}. */
    public static SimHarness forScenario(Scenario scenario, JobExecutor jobs) {
        return builder()
                .size(scenario.sizeX(), scenario.sizeZ())
                .seed(scenario.seed())
                .jobs(jobs)
                .build();
    }

    /** Builds a harness for {@code scenario} and plays it. */
    public static ScenarioReport run(Scenario scenario) {
        return forScenario(scenario).play(scenario);
    }

    /**
     * Plays {@code scenario} on this fresh harness: setup at tick 0, then step and onTick until isDone (checked at tick
     * 0 too) or the timeout, then verify at that tick. A scenario failure, a broken invariant or the timeout gives a
     * failed report; every report carries the state hash of the tick the run stopped at.
     */
    public ScenarioReport play(Scenario scenario) {
        try {
            scenario.setup(this);
            while (!scenario.isDone(this)) {
                if (tick() >= scenario.timeoutTicks()) {
                    return new ScenarioReport(
                            scenario.id(), false, tick(), stateHash(), prefix() + "timed out, isDone never held");
                }
                step();
                scenario.onTick(this);
            }
            scenario.verify(this);
            return new ScenarioReport(scenario.id(), true, tick(), stateHash(), null);
        } catch (InvariantViolation e) {
            return new ScenarioReport(scenario.id(), false, tick(), stateHash(), e.getMessage());
        } catch (ScenarioFailure e) {
            return new ScenarioReport(scenario.id(), false, tick(), stateHash(), prefix() + e.getMessage());
        }
    }

    public MatchCore core() {
        return core;
    }

    @Override
    public MatchQueries queries() {
        return core.queries();
    }

    public FakeBodies bodies() {
        return bodies;
    }

    /** Queues a command for the next tick, stamped with this tick and the harness's next sequence number. */
    @Override
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

    /**
     * Gives every coming tick the agents {@code perTick} lists for it, as the agent tracker does; an empty list is a
     * tick without an update.
     */
    public void agents(LongFunction<List<Agent>> perTick) {
        this.agents = perTick;
    }

    /** Queues damage a unit's body took for the next tick, as the damage bridge does. */
    public void damage(ExternalDamage damage) {
        pending.add(damage);
    }

    /** Queues a terrain page for the next tick, as a page job's delivery does. */
    @Override
    public void publish(TerrainPage page) {
        pending.add(page);
    }

    /** Every page of {@code world} for the next tick, as a match's prewarm does. */
    public void publishAll(VoxelWorld world) {
        PageBuilder builder = new PageBuilder(MobilityClass.INFANTRY);
        SurfaceGrid grid = core.queries().surfaces();
        for (int pz = 0; pz < grid.pagesZ(); pz++) {
            for (int px = 0; px < grid.pagesX(); px++) {
                PageCoord coord = new PageCoord(px, pz);
                publish(TerrainPage.build(coord, world.slice(coord), builder));
            }
        }
    }

    /** Adds {@code event} to the inputs of {@code tick}, such as the terrain a live match published then. */
    public void schedule(long tick, InputEvent event) {
        if (tick <= tick()) {
            throw new IllegalArgumentException("tick " + tick + " has passed; the harness is at " + tick());
        }
        scheduled.computeIfAbsent(tick, t -> new ArrayList<>()).add(event);
    }

    /** Steps one tick with the queued commands, applies the outputs to the bodies and checks the invariants. */
    public TickOutputs step() {
        List<InputEvent> due = scheduled.remove(tick() + 1);
        if (due != null) {
            pending.addAll(due);
        }
        List<Agent> present = agents == null ? List.of() : agents.apply(tick() + 1);
        if (!present.isEmpty()) {
            pending.add(new AgentUpdate(present));
        }
        if (core.queries().config().rules().delivery() == DeliveryMode.ADAPTIVE) {
            int[] finished = core.queries().finishedPathJobs(tick() + 1); // the adapter's part, as the mod's
            if (finished.length > 0) {
                pending.add(new JobDelivery(finished));
            }
        }
        TickOutputs out = core.step(new TickInputs(tick() + 1, pending));
        pending.clear();
        events.addAll(out.events());
        bodies.accept(out);
        violations = List.copyOf(invariants.check(core.queries(), out.tick()));
        assertInvariants();
        return out;
    }

    public void runTicks(int n) {
        for (int i = 0; i < n; i++) {
            step();
        }
    }

    /** Steps until {@code done} holds, checked before every step, for at most {@code maxTicks} steps; whether it did. */
    public boolean runUntil(BooleanSupplier done, int maxTicks) {
        for (int i = 0; i < maxTicks; i++) {
            if (done.getAsBoolean()) {
                return true;
            }
            step();
        }
        return done.getAsBoolean();
    }

    /** Every event of {@code type} since the harness was built. */
    public <E extends OutputEvent> List<E> events(Class<E> type) {
        return events.stream().filter(type::isInstance).map(type::cast).toList();
    }

    @Override
    public List<CommandResult> results() {
        return events(CommandResult.class);
    }

    @Override
    public List<UnitStateChanged> stateChanges() {
        return events(UnitStateChanged.class);
    }

    @Override
    public long tick() {
        return core.queries().tick();
    }

    @Override
    public long stateHash() {
        return core.stateHash();
    }

    /** Replays this match's journal from its start snapshot. */
    public ReplayResult replay() {
        return ReplayDriver.replay(core.journal(), core.queries().templates());
    }

    /** What the last invariant check found broken; empty before the first step. */
    public List<String> invariantViolations() {
        return violations;
    }

    /** Throws {@link InvariantViolation}, naming the seed and the tick, when the last check found a broken invariant. */
    public void assertInvariants() {
        if (!violations.isEmpty()) {
            throw new InvariantViolation(prefix() + String.join("; ", violations));
        }
    }

    private String prefix() {
        return "seed=" + seed + " tick=" + tick() + ": ";
    }

    /** A 32 x 32 match on a floor at y 1, seed 42, two factions, the built-in templates, unless told otherwise. */
    public static final class Builder {
        private MatchBounds bounds = new MatchBounds(32, 32, 0, 16, 1.0);
        private long seed = DEFAULT_SEED;
        private int factions = 2;
        private MatchRules rules = MatchRules.DEFAULTS;
        private TemplateSet templates = TemplateSet.builtin();
        private Clock clock = new FixedClock(0);
        private MetricsSink metrics = MetricsSink.none();
        private JobExecutor jobs = JobExecutor.inline();
        private MatchId id = new MatchId(1);
        private JournalSink journal = JournalSink.MEMORY;

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

        /** Where path and page jobs run: inline at their join unless told otherwise. */
        /** Where the journal goes and how much of it is kept (MATCH-04's ring); the last hour in memory by default. */
        public Builder journal(JournalSink journal) {
            this.journal = journal;
            return this;
        }

        public Builder jobs(JobExecutor jobs) {
            this.jobs = jobs;
            return this;
        }

        public SimHarness build() {
            MatchConfig config = new MatchConfig(id, seed, bounds, factions, rules);
            return new SimHarness(MatchCore.create(config, templates, new CorePorts(metrics, clock, jobs, journal)));
        }
    }
}
