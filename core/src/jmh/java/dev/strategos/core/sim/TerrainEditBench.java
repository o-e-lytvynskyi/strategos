package dev.strategos.core.sim;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.sim.nav.BlockClass;
import dev.strategos.core.sim.nav.MobilityClass;
import dev.strategos.core.sim.nav.PageBuilder;
import dev.strategos.core.sim.nav.PageCoord;
import dev.strategos.core.testkit.VoxelWorld;
import java.util.ArrayList;
import java.util.List;
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
 * M3 spec §5: what one of the core's own page rebuilds costs, a log removed (M3 T5): the page's last slice decoded, its
 * edit laid over it and the page built and published, in a 64-block band of rolling ground with trees. The edit is
 * then forgotten, as a capture after its write would, so every op rebuilds with one edit. It allocates its page.
 */
@Fork(2)
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class TerrainEditBench {
    private static final int HEIGHT = 64;

    private MatchState state;
    private int n;

    @Setup(Level.Trial)
    public void setUp() {
        VoxelWorld w = new VoxelWorld(64, HEIGHT, 64);
        short leaves = BlockClass.of(16, BlockClass.LEAVES);
        for (int x = 0; x < 64; x++) {
            for (int z = 0; z < 64; z++) {
                int ground = 30 + (int) Math.round(4 * Math.sin(x * 0.4) + 3 * Math.cos(z * 0.3));
                w.fill(x, 0, z, x, ground - 1, z, BlockClass.SOLID);
                if ((x * 7 + z * 5) % 23 == 0) {
                    w.fill(x, ground, z, x, ground + 3, z, BlockClass.SOLID);
                    w.fill(x, ground + 4, z, x, ground + 6, z, leaves);
                }
            }
        }
        MatchConfig config =
                new MatchConfig(new MatchId(1), 42, new MatchBounds(64, 64, 0, HEIGHT - 1, 30), 2, MatchRules.DEFAULTS);
        MatchCore core = MatchCore.create(config, TemplateSet.builtin(), CorePorts.of(System::nanoTime));
        List<InputEvent> pages = new ArrayList<>();
        PageBuilder builder = new PageBuilder(MobilityClass.INFANTRY);
        for (int pz = 0; pz < 4; pz++) {
            for (int px = 0; px < 4; px++) {
                PageCoord page = new PageCoord(px, pz);
                pages.add(TerrainPage.build(page, w.slice(page), builder));
            }
        }
        core.step(new TickInputs(1, pages));
        state = ((MatchCoreImpl) core).state();
    }

    @Benchmark
    public int rebuildOnePage() {
        int k = n++;
        int x = k % 16;
        int z = (k / 16) % 16;
        state.editVoxel(x, 50, z, (k & 256) == 0 ? BlockClass.SOLID : BlockClass.AIR);
        state.rebuildEdited();
        state.terrainEdits().dropUpTo(0, Long.MAX_VALUE);
        return state.changedPageCount();
    }
}
