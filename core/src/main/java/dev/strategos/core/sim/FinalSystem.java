package dev.strategos.core.sim;

import dev.strategos.core.diag.PhaseTimers;
import dev.strategos.core.port.JournalSink;
import dev.strategos.core.port.MetricsSink;
import dev.strategos.core.sim.journal.JournalExport;
import dev.strategos.core.sim.journal.JournalFormat;
import dev.strategos.core.sim.journal.JournalWriter;
import org.jspecify.annotations.Nullable;

/**
 * P8: checks that the bus came to rest, the treasury balances (ECO-01) and the gathered resources add up (ECO-05),
 * decides who lost and whether the match is over (MATCH-01),
 * builds the tick's outputs, records a state-hash checkpoint every
 * {@code checkpointEvery} ticks, at each segment boundary hands the kept journal to a sink that writes it and starts
 * the next segment on a fresh snapshot, and reports the phase times.
 */
final class FinalSystem implements MatchSystem {
    private final MetricsSink metrics;
    private final PhaseTimers timers;
    private final JournalWriter journal;
    private final JournalSink sink;
    private @Nullable TickOutputs outputs;

    FinalSystem(MetricsSink metrics, PhaseTimers timers, JournalWriter journal, JournalSink sink) {
        this.metrics = metrics;
        this.timers = timers;
        this.journal = journal;
        this.sink = sink;
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
        String treasury = state.treasury().broken();
        if (treasury != null) { // ECO-01: a few sums a tick, so every run checks it, a GameTest's and a server's too
            throw new IllegalStateException("the treasury is out of balance at tick " + state.tick() + ": " + treasury);
        }
        VictoryCheck.run(state); // MATCH-01: who lost, and whether the match is over
        String economy = state.economyBroken();
        if (economy != null) { // ECO-05: what nodes gave is carried, delivered or dropped, every step
            throw new IllegalStateException("the economy is out of balance at tick " + state.tick() + ": " + economy);
        }
        long tick = state.tick();
        outputs = new TickOutputs(tick, state.frame().build(), state.outputs());
        if (tick % journal.checkpointEvery() == 0) {
            journal.checkpoint(tick, StateHasher.hash(state));
        }
        if (journal.segmentDue()) {
            if (sink.writes()) {
                sink.write(
                        tick, JournalFormat.encode(new JournalExport(journal.view(), tick, StateHasher.hash(state))));
            }
            journal.startSegment(Snapshots.take(state));
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
