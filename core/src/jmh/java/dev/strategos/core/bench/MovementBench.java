package dev.strategos.core.bench;

import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.TickInputs;
import dev.strategos.core.sim.TickOutputs;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/** One {@code MatchCore.step} with 600 walking units: the core's share of budget A (spec 7.6). */
@Fork(2)
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class MovementBench {
    private MatchCore core;

    @Setup(Level.Trial)
    public void setUp() {
        core = BenchMatches.walking(300);
    }

    @Benchmark
    public TickOutputs step600() {
        long tick = core.queries().tick() + 1;
        TickInputs inputs =
                tick % 120 == 0 ? BenchMatches.reorder(core, tick, tick % 240 == 0) : TickInputs.empty(tick);
        return core.step(inputs);
    }
}
