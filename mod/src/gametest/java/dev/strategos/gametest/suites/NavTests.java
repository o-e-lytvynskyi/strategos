package dev.strategos.gametest.suites;

import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.nav.Dir;
import dev.strategos.core.sim.nav.SurfaceGrid;
import dev.strategos.core.sim.world.WritePriority;
import dev.strategos.core.sim.world.WriteSite;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.world.BlockWriter;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/** Terrain pages in a real level (PATH-15): rebuilt for other edits, never for the block writer's. */
public final class NavTests {
    private NavTests() {}

    /**
     * PATH-15.1: a block placed, then removed, outside the writer rebuilds its page within 40 ticks each time, and only
     * that page: the other three keep their hashes and nothing else is captured.
     */
    @StrategosTest(timeoutTicks = 200)
    public static void blockChangeRebuildsPages(GameTestHelper h) {
        MatchRuntime rt = MatchFixture.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        BlockPos rel = new BlockPos(5, 2, 5);
        var state = new Object() {
            long[] before;
            long placedAt;
            long capturedBefore;
        };
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(core(rt).queries().pageRefs().size() == 4, "prewarm"))
                .thenExecute(() -> {
                    state.before = hashes(rt);
                    state.capturedBefore = rt.pages().captured();
                    state.placedAt = rt.tick();
                    h.setBlock(rel, Blocks.STONE);
                })
                .thenWaitUntil(() -> h.assertTrue(hashes(rt)[0] != state.before[0], "page (0, 0) not rebuilt yet"))
                .thenExecute(() -> {
                    long[] after = hashes(rt);
                    h.assertTrue(rt.tick() - state.placedAt <= 40, "rebuilt after " + (rt.tick() - state.placedAt));
                    for (int i = 1; i < 4; i++) {
                        h.assertTrue(after[i] == state.before[i], "page " + i + " changed too");
                    }
                    h.assertTrue(rt.pages().captured() == state.capturedBefore + 1, "a full rebuild");
                    state.placedAt = rt.tick();
                    h.setBlock(rel, Blocks.AIR);
                })
                .thenWaitUntil(() -> h.assertTrue(hashes(rt)[0] == state.before[0], "page (0, 0) not restored yet"))
                .thenExecute(() -> h.assertTrue(
                        rt.tick() - state.placedAt <= 40, "restored after " + (rt.tick() - state.placedAt)))
                .thenSucceed();
    }

    /**
     * BLD-15.2, PATH-15.2: blocks the writer places, which change their nav class, dirty no page: 45 ticks after the
     * writes, nothing beyond the prewarm was captured and every page keeps its hash.
     */
    @StrategosTest(timeoutTicks = 200)
    public static void ownWritesNeverDirty(GameTestHelper h) {
        MatchRuntime rt = MatchFixture.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        BlockWriter writer = StrategosServer.get().writer();
        var state = new Object() {
            long[] before;
            long capturedBefore;
        };
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(core(rt).queries().pageRefs().size() == 4, "prewarm"))
                .thenExecute(() -> {
                    state.before = hashes(rt);
                    state.capturedBefore = rt.pages().captured();
                    for (int x = 2; x < 30; x += 3) {
                        for (int z = 2; z < 30; z += 3) {
                            writer.enqueue(
                                    h.getLevel(),
                                    new WriteSite(rt.id().value(), 1),
                                    WritePriority.CONSTRUCTION,
                                    h.absolutePos(new BlockPos(x, 2, z)),
                                    Blocks.STONE.defaultBlockState(),
                                    0);
                        }
                    }
                })
                // its own writes done: the level's queue holds the batch's other matches too (shared budget)
                .thenWaitUntil(() -> {
                    var space = rt.space();
                    h.assertTrue(
                            writer.pendingIn(
                                            h.getLevel(),
                                            rt.id().value(),
                                            new BlockPos(
                                                    space.origin().getX(),
                                                    space.minY(),
                                                    space.origin().getZ()),
                                            new BlockPos(
                                                    space.origin().getX() + space.sizeX() - 1,
                                                    space.maxY(),
                                                    space.origin().getZ() + space.sizeZ() - 1))
                                    .isEmpty(),
                            "writing");
                })
                .thenIdle(45)
                .thenExecute(() -> {
                    h.assertTrue(rt.pages().dirtyPages() == 0, rt.pages().dirtyPages() + " pages dirty");
                    h.assertTrue(
                            rt.pages().captured() == state.capturedBefore,
                            (rt.pages().captured() - state.capturedBefore) + " pages captured after own writes");
                    long[] after = hashes(rt);
                    for (int i = 0; i < 4; i++) {
                        h.assertTrue(after[i] == state.before[i], "page " + i + " was rebuilt");
                    }
                })
                .thenSucceed();
    }

    /**
     * PATH-04.3: a wall three high across the arena at x 16, placed outside the writer, splits its floor in two; within
     * 40 ticks neither half reaches the other, and from either half and from the wall's top what the components say is
     * reachable equals a flood fill along the grid's links.
     */
    @StrategosTest(timeoutTicks = 200)
    public static void wallSplitsComponents(GameTestHelper h) {
        MatchRuntime rt = MatchFixture.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        var state = new Object() {
            long placedAt;
        };
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(core(rt).queries().pageRefs().size() == 4, "prewarm"))
                .thenExecute(() -> {
                    SurfaceGrid grid = core(rt).queries().surfaces();
                    h.assertTrue(
                            grid.component(grid.node(4, 4, 0)) == grid.component(grid.node(28, 4, 0)),
                            "the floor is split before the wall");
                    state.placedAt = rt.tick();
                    for (int z = 0; z < 32; z++) {
                        for (int y = 2; y <= 4; y++) { // relative y 1 is the floor layer
                            h.setBlock(new BlockPos(16, y, z), Blocks.STONE);
                        }
                    }
                })
                .thenWaitUntil(() -> {
                    SurfaceGrid grid = core(rt).queries().surfaces();
                    h.assertTrue(
                            !grid.reachableFrom(grid.node(4, 4, 0)).get(grid.component(grid.node(28, 4, 0))),
                            "the west half still reaches the east");
                })
                .thenExecute(() -> {
                    h.assertTrue(rt.tick() - state.placedAt <= 40, "split after " + (rt.tick() - state.placedAt));
                    SurfaceGrid grid = core(rt).queries().surfaces();
                    for (int start : new int[] {grid.node(4, 4, 0), grid.node(28, 4, 0), grid.node(16, 4, 0)}) {
                        String mismatch = floodFillMismatch(grid, start);
                        h.assertTrue(mismatch == null, String.valueOf(mismatch));
                    }
                })
                .thenSucceed();
    }

    /** Null when the nodes a flood fill along the links reaches from {@code start} are those the components say. */
    private static @Nullable String floodFillMismatch(SurfaceGrid grid, int start) {
        Set<Integer> filled = new TreeSet<>(List.of(start));
        ArrayDeque<Integer> open = new ArrayDeque<>(List.of(start));
        while (!open.isEmpty()) {
            int node = open.poll();
            int x = grid.nodeX(node);
            int z = grid.nodeZ(node);
            for (int dir = 0; dir < Dir.COUNT; dir++) {
                int nl = grid.neighbour(x, z, SurfaceGrid.nodeLayer(node), dir);
                if (nl >= 0 && filled.add(grid.node(x + Dir.dx(dir), z + Dir.dz(dir), nl))) {
                    open.add(grid.node(x + Dir.dx(dir), z + Dir.dz(dir), nl));
                }
            }
        }
        BitSet reachable = grid.reachableFrom(start);
        for (int x = 0; x < grid.sizeX(); x++) {
            for (int z = 0; z < grid.sizeZ(); z++) {
                for (int layer = 0; layer < grid.count(x, z); layer++) {
                    int node = grid.node(x, z, layer);
                    if (reachable.get(grid.component(node)) != filled.contains(node)) {
                        return "from node " + start + ", node (" + x + ", " + z + ", " + layer + "): components say "
                                + reachable.get(grid.component(node)) + ", the flood fill " + filled.contains(node);
                    }
                }
            }
        }
        return null;
    }

    private static MatchCore core(MatchRuntime rt) {
        return Objects.requireNonNull(rt.core());
    }

    /** The four page hashes of a 32 x 32 match: (0, 0), (1, 0), (0, 1), (1, 1). */
    private static long[] hashes(MatchRuntime rt) {
        SurfaceGrid grid = core(rt).queries().surfaces();
        return new long[] {
            grid.page(0, 0).hash(),
            grid.page(1, 0).hash(),
            grid.page(0, 1).hash(),
            grid.page(1, 1).hash()
        };
    }
}
