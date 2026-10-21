package dev.strategos.core.sim;

import dev.strategos.core.diag.PhaseTimers;
import dev.strategos.core.port.MetricsSink;
import dev.strategos.core.sim.journal.JournalWriter;
import org.jspecify.annotations.Nullable;

/**
 * P8: checks that the bus came to rest, builds the tick's outputs, records a state-hash checkpoint every
 * {@code checkpointEvery} ticks, re-bases the journal on a fresh snapshot past its limit, and reports the phase times.
 */
final class FinalSystem implements MatchSystem {
    private final MetricsSink metrics;
    private final PhaseTimers timers;
    private final JournalWriter journal;
    private @Nullable TickOutputs outputs;

    FinalSystem(MetricsSink metrics, PhaseTimers timers, JournalWriter journal) {
        this.metrics = metrics;
        this.timers = timers;
        this.journal = journal;
    }

    @Override
    public Phase phase() {
        return Phase.FINAL;
    }

    @Override
    public void run(MatchState state) {
        if (!state.bus().idle()) {
            throw new IllegalStateException("the match bus is still dispatching at the end of tick " + state.tick());
        }
        long tick = state.tick();
        outputs = new TickOutputs(tick, state.frame().build(), state.outputs());
        if (tick % journal.checkpointEvery() == 0) {
            journal.checkpoint(tick, StateHasher.hash(state));
        }
        if (journal.needsRebase()) {
            journal.rebase(Snapshots.take(state));
        }
        metrics.onTick(tick, timers.currentPhaseNanos());
    }

    /** What the last step built. */
    TickOutputs outputs() {
        if (outputs == null) {
            throw new IllegalStateException("no tick has finished yet");
        }
        return outputs;
    }
}
