package dev.strategos.core.bench;

import dev.strategos.core.sim.vision.DiscOffsets;
import dev.strategos.core.sim.vision.VisionGrid;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * FOW-01: the vision grids' cost per tick with 300 units of two factions wandering a 384² match: each unit steps a
 * little and, when it crosses a cell, its disc is unstamped from the old cell and stamped on the new one.
 */
@Fork(2)
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class VisionBench {
    private static final int UNITS = 300;
    private static final int SIZE = 384;
    private static final int RADIUS = 12 / DiscOffsets.CELL;

    private final Random random = new Random(42);
    private final VisionGrid[] grids = {new VisionGrid(SIZE, SIZE), new VisionGrid(SIZE, SIZE)};
    private final double[] xs = new double[UNITS];
    private final double[] zs = new double[UNITS];
    private final int[] cellX = new int[UNITS];
    private final int[] cellZ = new int[UNITS];

    @Setup
    public void setUp() {
        for (int k = 0; k < UNITS; k++) {
            xs[k] = 8 + random.nextDouble() * (SIZE - 16);
            zs[k] = 8 + random.nextDouble() * (SIZE - 16);
            cellX[k] = VisionGrid.cellOf(xs[k]);
            cellZ[k] = VisionGrid.cellOf(zs[k]);
            grids[k & 1].stamp(cellX[k], cellZ[k], RADIUS);
        }
    }

    @Benchmark
    public int moveAndRestamp() {
        int moved = 0;
        for (int k = 0; k < UNITS; k++) {
            xs[k] = clamp(xs[k] + random.nextDouble() * 4 - 2);
            zs[k] = clamp(zs[k] + random.nextDouble() * 4 - 2);
            int cx = VisionGrid.cellOf(xs[k]);
            int cz = VisionGrid.cellOf(zs[k]);
            if (cx != cellX[k] || cz != cellZ[k]) {
                VisionGrid grid = grids[k & 1];
                grid.unstamp(cellX[k], cellZ[k], RADIUS);
                grid.stamp(cx, cz, RADIUS);
                cellX[k] = cx;
                cellZ[k] = cz;
                moved++;
            }
        }
        grids[0].updateExplored();
        grids[1].updateExplored();
        return moved;
    }

    private static double clamp(double v) {
        return Math.max(1, Math.min(SIZE - 1, v));
    }
}
