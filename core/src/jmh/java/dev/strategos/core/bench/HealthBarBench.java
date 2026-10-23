package dev.strategos.core.bench;

import dev.strategos.core.client.hud.HealthBars;
import java.util.ArrayList;
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

/** CMB-06.2: one frame's health bars for 300 units, a third selected and a third hurt (budget: 0.5 ms). */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class HealthBarBench {
    private List<HealthBars.Unit> units = List.of();

    @Setup(Level.Trial)
    public void setUp() {
        List<HealthBars.Unit> list = new ArrayList<>();
        for (int k = 0; k < 300; k++) {
            list.add(new HealthBars.Unit(k % 40, 64, k / 40.0, 1.8, 0.6, k % 3 == 0, k % 3 == 1 ? 100 + k % 100 : 255));
        }
        units = List.copyOf(list);
    }

    @Benchmark
    public Object bars300() {
        return HealthBars.extract(units);
    }
}
