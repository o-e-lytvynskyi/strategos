package dev.strategos.core.bench;

import dev.strategos.core.sim.econ.EconomyRules;
import dev.strategos.core.sim.econ.NodeDiscovery;
import dev.strategos.core.sim.econ.NodeStore;
import dev.strategos.core.sim.nav.Material;
import dev.strategos.core.sim.nav.MaterialKind;
import dev.strategos.core.sim.nav.PageCoord;
import dev.strategos.core.sim.nav.VoxelSlice;
import dev.strategos.core.testkit.ProvingGround;
import dev.strategos.core.testkit.VoxelWorld;
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
 * ECO-02.5 (L6): indexing the proving ground at prepare, under 2 s: its 576 slices decoded from their encoded form, as
 * the match keeps them, and every log and leaf of its forest found, the oaks joined into trees.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class NodeDiscoveryBench {
    private static final Material LOG = new Material(MaterialKind.LOG, "wood", "tree", 20_000);
    private static final Material LEAF = new Material(MaterialKind.LEAVES, "", "", 0);

    private byte[][] slices;
    private int pages;

    @Setup(Level.Trial)
    public void setUp() {
        ProvingGround pg = ProvingGround.generate();
        VoxelWorld w = pg.voxels();
        for (int x = 0; x < pg.size(); x++) {
            for (int z = 0; z < pg.size(); z++) {
                for (int y = 0; y < ProvingGround.HEIGHT; y++) {
                    byte b = pg.block(x, y, z);
                    if (b == ProvingGround.LOG) {
                        w.material(x, y, z, LOG);
                    } else if (b == ProvingGround.LEAVES) {
                        w.material(x, y, z, LEAF);
                    }
                }
            }
        }
        pages = pg.size() / VoxelSlice.SIDE;
        slices = new byte[pages * pages][];
        for (int p = 0; p < slices.length; p++) {
            slices[p] = w.slice(new PageCoord(p % pages, p / pages)).encode();
        }
    }

    @Benchmark
    public int provingGround() {
        NodeStore store = new NodeStore();
        NodeDiscovery.discover(store, EconomyRules.DEFAULTS, pages, pages, 0, p -> VoxelSlice.decode(slices[p]));
        if (store.count() != 100) { // map v2 (M3 T9): the forest's 60 oaks, the groves' 24 and 16 berry bushes
            throw new IllegalStateException(store.count() + " nodes, not the proving ground's 100");
        }
        return store.count();
    }
}
