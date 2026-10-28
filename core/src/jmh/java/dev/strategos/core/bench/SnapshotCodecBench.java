package dev.strategos.core.bench;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
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

/** Encoding a 500-unit snapshot through JsonOps: a proxy for budget G, whose NBT path L3 measures (spec 7.6). */
@Fork(2)
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class SnapshotCodecBench {
    private MatchSnapshot snapshot;

    @Setup(Level.Trial)
    public void setUp() {
        snapshot = BenchMatches.walking(250).snapshot();
    }

    @Benchmark
    public JsonElement encode500() {
        return MatchSnapshotCodec.encode(JsonOps.INSTANCE, snapshot);
    }
}
