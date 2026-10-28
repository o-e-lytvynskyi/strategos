package dev.strategos.core.bench;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.EntityStore;
import dev.strategos.core.sim.IdentityStore;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.spatial.UnitFilter;
import dev.strategos.core.sim.spatial.UnitGrid;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
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
 * CORE-08.3: the unit grid with 1,000 units of two factions on a 384² match, crowded into a 128² battle in its middle.
 * {@code rebuild1000} rebuilds it (a {@code @HotPath} kernel: no allocation); {@code query1000} asks, for each unit,
 * for the enemies within 16 blocks of it (budget: 1 ms for the 1,000).
 */
@Fork(2)
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class SpatialBench {
    private static final int UNITS = 1_000;
    private static final int SIZE = 384;

    private final EntityStore entities = new EntityStore();
    private final MotionStore motion = new MotionStore();
    private final IdentityStore identity = new IdentityStore();
    private final UnitFilter[] enemies = {UnitFilter.allBut(0), UnitFilter.allBut(1)};
    private final double[] xs = new double[UNITS];
    private final double[] zs = new double[UNITS];
    private UnitGrid grid;
    private long sum;
    private final IntConsumer add = i -> sum += i;

    @Setup(Level.Trial)
    public void setUp() {
        Random r = new Random(42);
        TemplateSet templates = TemplateSet.builtin();
        for (int k = 0; k < UNITS; k++) {
            int i = Handle.index(entities.create());
            motion.ensureCapacity(entities.highWater());
            identity.ensureCapacity(entities.highWater());
            xs[k] = 128 + r.nextDouble() * 128;
            zs[k] = 128 + r.nextDouble() * 128;
            motion.set(i, xs[k], 0, zs[k], 0);
            identity.set(i, k % templates.all().size(), k & 1);
        }
        grid = new UnitGrid(SIZE, SIZE, templates);
        grid.rebuild(entities, motion, identity);
    }

    @Benchmark
    @Fork(5) // bimodal across forks on the M4 Pro: five keep one slow fork from deciding the mean
    public int rebuild1000() {
        grid.rebuild(entities, motion, identity);
        return grid.count();
    }

    @Benchmark
    public long query1000() {
        sum = 0;
        for (int k = 0; k < UNITS; k++) {
            grid.forEachInRadius(xs[k], zs[k], 16, enemies[k & 1], add);
        }
        return sum;
    }
}
