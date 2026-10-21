package dev.strategos.core.sim;

import dev.strategos.core.diag.PhaseTimers;
import dev.strategos.core.port.MetricsSink;
import org.jspecify.annotations.Nullable;

/**
 * P8: builds the tick's outputs, reports the tick's phase times and checks that the bus came to rest. T6a also
 * checkpoints and re-bases the journal here.
 */
final class FinalSystem implements MatchSystem {
    private final MetricsSink metrics;
    private final PhaseTimers timers;
    private @Nullable TickOutputs outputs;

    FinalSystem(MetricsSink metrics, PhaseTimers timers) {
        this.metrics = metrics;
        this.timers = timers;
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
        outputs = new TickOutputs(state.tick(), state.frame().build(), state.outputs());
        metrics.onTick(state.tick(), timers.currentPhaseNanos());
    }

    /** What the last step built. */
    TickOutputs outputs() {
        if (outputs == null) {
            throw new IllegalStateException("no tick has finished yet");
        }
        return outputs;
    }
}
