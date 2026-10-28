package dev.strategos.core.bench;

import dev.strategos.core.port.JobScratch;
import dev.strategos.core.sim.nav.GridAStar;
import dev.strategos.core.sim.nav.PathJob;
import dev.strategos.core.sim.nav.PathRequest;
import dev.strategos.core.sim.nav.PathResult;
import dev.strategos.core.sim.nav.SurfaceGrid;
import dev.strategos.core.testkit.ProvingGround;
import java.util.Locale;
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

/**
 * PATH-03.5 and PATH-08.2: one unit's path from the west start to the east start of the generated proving ground,
 * 384², and of its 512² version, over the river by the ford or a bridge, as a {@code STRICT} join runs it on the server
 * thread: {@code cross384} and {@code cross512} through the path service's job (two levels, HPA*, since T28), {@code
 * flat384} and {@code flat512} through flat A* at its 20,000 cap, which R3 measured in T8. Sampled, so the report has
 * p95; each trial prints the expansions and waypoints. Budget D gives the main thread 2 ms a tick for all path work.
 */
@Fork(2)
@State(Scope.Thread)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class CrossingBench {
    private final JobScratch scratch = new JobScratch();
    private final GridAStar flat = new GridAStar();
    private PathJob job384;
    private PathJob job512;

    @Setup(Level.Trial)
    public void setUp() {
        job384 = crossing(384);
        job512 = crossing(512);
    }

    @Benchmark
    public PathResult cross384() {
        return job384.run(scratch);
    }

    @Benchmark
    public PathResult cross512() {
        return job512.run(scratch);
    }

    @Benchmark
    public GridAStar.Result flat384() {
        return flat(job384);
    }

    @Benchmark
    public GridAStar.Result flat512() {
        return flat(job512);
    }

    private GridAStar.Result flat(PathJob job) {
        PathRequest r = job.request();
        return flat.search(job.grid(), r.fromNode(), (int) r.goalX(), (int) r.goalZ(), r.cap());
    }

    private PathJob crossing(int size) {
        SurfaceGrid grid = ProvingGround.generate(size).voxels().grid();
        int middle = size / 2;
        int from = grid.node(24, middle, 0);
        PathJob job =
                new PathJob(grid, new PathRequest(1, 1, 0, from, size - 24 + 0.5, middle + 0.5, 20_000, 0, false));
        PathResult result = job.run(scratch);
        GridAStar.Result flatResult = flat(job);
        System.out.printf(
                Locale.ROOT,
                "crossing %d: %s, %d expansions, %d waypoints; flat %s, %d expansions%n",
                size,
                result.status(),
                result.expansions(),
                result.waypoints().count(),
                flatResult.status(),
                flatResult.expansions());
        return job;
    }
}
