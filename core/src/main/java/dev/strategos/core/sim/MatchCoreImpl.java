package dev.strategos.core.sim;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.diag.MatchLog;
import dev.strategos.core.diag.PhaseTimers;
import dev.strategos.core.port.MetricsSink;
import dev.strategos.core.protocol.CommandKinds;
import dev.strategos.core.sim.command.CommandRules;
import dev.strategos.core.sim.command.CommandSystem;
import dev.strategos.core.sim.journal.Journal;
import dev.strategos.core.sim.journal.JournalWriter;
import dev.strategos.core.sim.move.MovementSystem;
import dev.strategos.core.sim.unitai.UnitAiSystem;

/** The match kernel: the systems registered in phase order and the nine-phase step. */
final class MatchCoreImpl implements MatchCore {
    /** The timers' window: 30 seconds of ticks. */
    private static final int TIMER_WINDOW = 600;

    private final MatchState state;
    private final Scheduler scheduler = new Scheduler();
    private final PhaseTimers timers;
    private final MetricsSink metrics;
    private final FinalSystem finalSystem;
    private final QueriesImpl queries;
    private final JournalWriter journal;
    private final Phase[] phases = Phase.values();

    private MatchCoreImpl(MatchState state, CorePorts ports, int checkpointEvery, int maxJournalTicks) {
        CommandRules rules = CommandRules.m0();
        rules.verifyCovers(CommandKinds.DEFAULT);
        this.state = state;
        this.timers = new PhaseTimers(Phase.names(), ports.clock(), TIMER_WINDOW);
        this.metrics = ports.metrics();
        this.journal = new JournalWriter(Snapshots.take(state), checkpointEvery, maxJournalTicks);
        CommandSystem commands = new CommandSystem(state, rules);
        scheduler.register(new IngestSystem(commands, journal));
        scheduler.register(commands);
        scheduler.register(new UnitAiSystem(state));
        scheduler.register(new MovementSystem());
        this.finalSystem = new FinalSystem(metrics, timers, journal);
        scheduler.register(finalSystem);
        this.queries = new QueriesImpl(state);
    }

    static MatchCoreImpl create(
            MatchConfig config, TemplateSet templates, CorePorts ports, int checkpointEvery, int maxJournalTicks) {
        MatchState state = new MatchState(config, templates, new EntityStore(), MatchLog.forSim(config.id()));
        return new MatchCoreImpl(state, ports, checkpointEvery, maxJournalTicks);
    }

    static MatchCoreImpl restore(
            MatchSnapshot snapshot, TemplateSet templates, CorePorts ports, int checkpointEvery, int maxJournalTicks) {
        MatchState state = Snapshots.restore(
                snapshot, templates, MatchLog.forSim(snapshot.config().id()));
        return new MatchCoreImpl(state, ports, checkpointEvery, maxJournalTicks);
    }

    @Override
    public TickOutputs step(TickInputs in) {
        if (in.tick() != state.tick() + 1) {
            throw new IllegalArgumentException("inputs for tick " + in.tick() + " after tick " + state.tick()
                    + "; the next is " + (state.tick() + 1));
        }
        state.beginTick(in);
        timers.beginTick();
        for (int i = 0; i < phases.length; i++) {
            if (i == 0) {
                timers.beginPhase(0);
            } else {
                timers.nextPhase(i - 1, i);
            }
            metrics.onPhase(in.tick(), i);
            scheduler.runPhase(phases[i], state);
        }
        timers.endPhase(phases.length - 1);
        timers.endTick();
        return finalSystem.outputs();
    }

    @Override
    public MatchQueries queries() {
        return queries;
    }

    @Override
    public long stateHash() {
        return StateHasher.hash(state);
    }

    @Override
    public MatchSnapshot snapshot() {
        return Snapshots.take(state);
    }

    @Override
    public Journal journal() {
        return journal.view();
    }

    @Override
    public PhaseTimers timers() {
        return timers;
    }

    /** For the kernel's own tests. */
    MatchState state() {
        return state;
    }
}
