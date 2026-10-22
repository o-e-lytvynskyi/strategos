package dev.strategos.gametest.suites;

import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.nav.SurfaceGrid;
import dev.strategos.core.sim.world.WritePriority;
import dev.strategos.core.sim.world.WriteSite;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.world.BlockWriter;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

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
                .thenWaitUntil(() -> h.assertTrue(writer.pending(h.getLevel()) == 0, "writing"))
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
