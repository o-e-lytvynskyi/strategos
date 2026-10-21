package dev.strategos.core.bench;

import dev.strategos.core.sim.MatchCore;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/** {@code stateHash()} with 600 units: a {@code @HotPath} kernel, so it must not allocate (QA-07.4). */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class StateHashBench {
    private MatchCore core;

    @Setup(Level.Trial)
    public void setUp() {
        core = BenchMatches.walking(300);
        for (int i = 0; i < 20; i++) {
            BenchMatches.step(core); // a few walking steps, so poses are not the spawn's
        }
    }

    @Benchmark
    public long hash600() {
        return core.stateHash();
    }
}
