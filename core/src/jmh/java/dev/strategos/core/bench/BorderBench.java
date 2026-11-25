package dev.strategos.core.bench;

import dev.strategos.core.client.territory.BorderGeometry;
import dev.strategos.core.shared.TerritoryGrid;
import java.util.concurrent.TimeUnit;
import java.util.function.IntBinaryOperator;
import java.util.function.IntPredicate;
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
 * TERR-03: the borders of one 16 × 16-block section column in a busy four-faction layout (tiles in a patchwork of
 * 3-tile blocks of every faction, a third of them unconnected, so every edge is open and many are dashed), into a
 * reused buffer (a {@code @HotPath} kernel: no allocation once warm).
 */
@Fork(2)
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class BorderBench {
    private final IntPredicate allied = BorderBench::isAlly;
    private final IntBinaryOperator floor = BorderBench::floorAt;

    private final BorderGeometry.Buffer out = new BorderGeometry.Buffer();
    private TerritoryGrid grid = new TerritoryGrid(1, 1);
    private int sectionX;
    private int sectionZ;

    @Setup(Level.Trial)
    public void setUp() {
        grid = new TerritoryGrid(48, 48);
        for (int tz = 0; tz < 48; tz++) {
            for (int tx = 0; tx < 48; tx++) {
                int faction = (tx / 3 + tz / 3) % 4;
                int connected = (tx + 2 * tz) % 3 == 0 ? 0 : TerritoryGrid.CONNECTED;
                grid.set(tz * 48 + tx, (byte) (faction | connected));
            }
        }
        sectionX = 10;
        sectionZ = 10;
        out.clear();
        BorderGeometry.extract(grid, sectionX, sectionZ, 0, allied, floor, out); // warm the buffer
    }

    @Benchmark
    public int extractSection() {
        BorderGeometry.extract(grid, sectionX, sectionZ, 0, allied, floor, out);
        return out.count();
    }

    private static boolean isAlly(int faction) {
        return faction == 2;
    }

    private static int floorAt(int x, int z) {
        return 64 + ((x ^ z) & 3);
    }
}
