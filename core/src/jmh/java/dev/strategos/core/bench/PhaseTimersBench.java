package dev.strategos.core.bench;

import dev.strategos.core.diag.PhaseTimers;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * The timers' own cost per step, called as {@code MatchCoreImpl.step} calls them for its nine phases; reported as a
 * share of step600 (CORE-02.8: below 1 %).
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class PhaseTimersBench {
    private static final int PHASES = 9;

    private PhaseTimers timers;

    @Setup(Level.Trial)
    public void setUp() {
        List<String> names = List.of("p0", "p1", "p2", "p3", "p4", "p5", "p6", "p7", "p8");
        timers = new PhaseTimers(names, System::nanoTime, 600);
    }

    @Benchmark
    public long overhead() {
        timers.beginTick();
        timers.beginPhase(0);
        for (int i = 1; i < PHASES; i++) {
            timers.nextPhase(i - 1, i);
        }
        timers.endPhase(PHASES - 1);
        return timers.endTick();
    }
}
