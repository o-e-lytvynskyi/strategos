package dev.strategos.core.bench;

import dev.strategos.core.sim.nav.BlockClass;
import dev.strategos.core.sim.nav.MobilityClass;
import dev.strategos.core.sim.nav.NavOverlay;
import dev.strategos.core.sim.nav.PageBuilder;
import dev.strategos.core.sim.nav.PageCoord;
import dev.strategos.core.sim.nav.SurfacePage;
import dev.strategos.core.sim.nav.VoxelSlice;
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
 * One page built from a hilly slice in a 64-block band, with trees, a pond and a bridge (PATH-02.4, S8's third exit
 * number: ≤ 0.5 ms). A build allocates its page, so it is not a zero-allocation kernel.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class PageBuilderBench {
    private static final int HEIGHT = 64;

    private VoxelSlice slice;
    private PageBuilder builder;
    private PageCoord page;

    @Setup(Level.Trial)
    public void setUp() {
        short leaves = BlockClass.of(16, BlockClass.LEAVES);
        short water = BlockClass.of(0, BlockClass.PASSABLE | BlockClass.WATER);
        short[] voxels = new short[VoxelSlice.SIDE * VoxelSlice.SIDE * HEIGHT];
        java.util.Arrays.fill(voxels, BlockClass.AIR);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int ground = 30 + (int) Math.round(4 * Math.sin(x * 0.4) + 3 * Math.cos(z * 0.3));
                for (int y = 0; y < ground; y++) {
                    voxels[(x * 16 + z) * HEIGHT + y] = BlockClass.SOLID;
                }
                if (x >= 10 && z >= 10) {
                    voxels[(x * 16 + z) * HEIGHT + ground] = water;
                }
                if ((x * 7 + z * 5) % 23 == 0) {
                    for (int y = ground + 4; y < ground + 7; y++) {
                        voxels[(x * 16 + z) * HEIGHT + y] = leaves;
                    }
                }
                if (z == 4) {
                    voxels[(x * 16 + z) * HEIGHT + 40] = BlockClass.SOLID;
                }
            }
        }
        slice = new VoxelSlice(HEIGHT, voxels);
        builder = new PageBuilder(MobilityClass.INFANTRY);
        page = new PageCoord(0, 0);
    }

    @Benchmark
    public SurfacePage build() {
        return builder.build(slice, NavOverlay.EMPTY, page);
    }
}
