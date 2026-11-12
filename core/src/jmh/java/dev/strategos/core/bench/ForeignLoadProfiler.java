package dev.strategos.core.bench;

import dev.strategos.core.testkit.ForeignLoad;
import java.lang.management.ManagementFactory;
import java.util.Collection;
import java.util.List;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.profile.InternalProfiler;
import org.openjdk.jmh.results.AggregationPolicy;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.ScalarResult;

/**
 * Foreign CPU load per iteration (ADR-0026 part B): the machine's busy fraction minus this JVM's own, as fractions
 * 0..1. The gate (core/build.gradle, {@code JmhGate}) calls a benchmark invalid when {@code foreign.mean} is above
 * {@code foreign.limit}, or the machine's load could not be read ({@code foreign.unavailable}).
 */
public final class ForeignLoadProfiler implements InternalProfiler {
    private final com.sun.management.OperatingSystemMXBean os =
            (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    private final int cpus = Runtime.getRuntime().availableProcessors();
    private long startNanos;
    private long startProcessCpu;

    @Override
    public String getDescription() {
        return "Foreign CPU load per iteration: the machine's busy fraction minus this JVM's own";
    }

    @Override
    public void beforeIteration(BenchmarkParams benchmarkParams, IterationParams iterationParams) {
        os.getCpuLoad(); // measures since its previous call: this call restarts the interval (its value is unused)
        startProcessCpu = os.getProcessCpuTime();
        startNanos = System.nanoTime();
    }

    @Override
    public Collection<? extends Result> afterIteration(
            BenchmarkParams benchmarkParams, IterationParams iterationParams, IterationResult result) {
        long wall = System.nanoTime() - startNanos;
        long processCpu = os.getProcessCpuTime() - startProcessCpu;
        double system = os.getCpuLoad();
        if (Double.isNaN(system) || system < 0 || wall <= 0 || processCpu < 0) {
            return List.of(
                    new ScalarResult("·foreign.unavailable", 1, "flag", AggregationPolicy.MAX),
                    new ScalarResult("·foreign.limit", ForeignLoad.meanLimit(cpus), "fraction", AggregationPolicy.AVG));
        }
        double own = Math.min(1, (double) processCpu / ((double) wall * cpus));
        double foreign = Math.max(0, system - own);
        return List.of(
                new ScalarResult("·foreign.mean", foreign, "fraction", AggregationPolicy.AVG),
                new ScalarResult("·foreign.peak", foreign, "fraction", AggregationPolicy.MAX),
                new ScalarResult("·foreign.limit", ForeignLoad.meanLimit(cpus), "fraction", AggregationPolicy.AVG));
    }
}
