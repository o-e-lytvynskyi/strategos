package dev.strategos.core.bench;

import dev.strategos.core.client.fog.ShroudExtract;
import java.util.Random;
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
 * FOW-07: shroud extraction for a 384² match (96×96 vision cells, 6×6 pages). Measures the pure mesh rebuild; draw cost
 * and budget F wait for PERF-14 in T27.
 */
@Fork(2)
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class ShroudBench {
    private static final int BLOCKS = 384;
    private static final int CELLS = BLOCKS / ShroudExtract.CELL_BLOCKS;

    private long[] visible = new long[0];
    private long[] explored = new long[0];
    private ShroudExtract.PageMesh[] pages = new ShroudExtract.PageMesh[0];
    private final Random random = new Random(7);

    @Setup(Level.Trial)
    public void setUp() {
        visible = new long[ShroudExtract.words(CELLS, CELLS)];
        explored = new long[visible.length];
        // A scouted disc near the centre, the rest unexplored; explored trails the visible disc.
        int cx = CELLS / 2;
        int cz = CELLS / 2;
        int visibleR = 8;
        int exploredR = 14;
        for (int z = 0; z < CELLS; z++) {
            for (int x = 0; x < CELLS; x++) {
                int dx = x - cx;
                int dz = z - cz;
                int d2 = dx * dx + dz * dz;
                int i = z * CELLS + x;
                if (d2 <= exploredR * exploredR) {
                    ShroudExtract.setBit(explored, i);
                }
                if (d2 <= visibleR * visibleR) {
                    ShroudExtract.setBit(visible, i);
                }
            }
        }
        int n = ShroudExtract.pages(CELLS) * ShroudExtract.pages(CELLS);
        pages = new ShroudExtract.PageMesh[n];
        for (int k = 0; k < n; k++) {
            pages[k] = new ShroudExtract.PageMesh();
        }
        ShroudExtract.extractAll(visible, explored, CELLS, CELLS, pages);
    }

    @Setup(Level.Invocation)
    public void dirtyOnePage() {
        // Flip one cell on a random page so extractAll must rebuild that page's mesh.
        int x = random.nextInt(CELLS);
        int z = random.nextInt(CELLS);
        int i = z * CELLS + x;
        visible[i >> 6] ^= 1L << (i & 63);
        explored[i >> 6] |= 1L << (i & 63);
    }

    @Benchmark
    public int extract384() {
        return ShroudExtract.extractAll(visible, explored, CELLS, CELLS, pages);
    }
}
