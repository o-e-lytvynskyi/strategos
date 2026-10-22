package dev.strategos.gametest.suites;

import dev.strategos.core.sim.world.WritePriority;
import dev.strategos.core.sim.world.WriteScheduler;
import dev.strategos.core.sim.world.WriteSite;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.world.BlockWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** The block writer (BLD-15) in a real level. */
public final class WriterTests {
    private static final int PER_SITE = 120;
    private static final int OWNER = 7;

    private WriterTests() {}

    /** One write as the writer made it. */
    private record Wrote(long tick, WriteSite site, BlockPos pos) {}

    /** What a site asked for: the writes in order. */
    private record Asked(WriteSite site, BlockPos pos, BlockState state, int owner) {}

    /**
     * BLD-15.1 and .3: saturated queues in two matches, two sites each. At most 64 writes a tick, each site at most
     * every 4 ticks, every write with {@code OwnWrite} set, in exactly the order {@link WriteScheduler} gives the same
     * queues, and the protected blocks recorded. Solo: it watches the server's one writer.
     */
    @StrategosTest(batch = "writer", timeoutTicks = 200, solo = true)
    public static void budgetHoldsAcrossTwoMatches(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockWriter writer = StrategosServer.get().writer();
        MatchRuntime a = MatchFixture.create(h, 0, 0, 14, 32, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        MatchRuntime b = MatchFixture.create(h, 18, 0, 14, 32, MatchFixture.options(SimHarness.DEFAULT_SEED + 1, h));
        List<Asked> asked = new ArrayList<>();
        for (MatchRuntime rt : List.of(a, b)) {
            for (int site = 1; site <= 2; site++) {
                BlockPos origin = rt.space().origin().above(2);
                for (int i = 0; i < PER_SITE; i++) {
                    BlockPos pos = origin.offset(i % 14, i / 14 % 2, (site - 1) * 16 + i / 28);
                    asked.add(new Asked(
                            new WriteSite(rt.id().value(), site),
                            pos,
                            Blocks.STONE.defaultBlockState(),
                            site == 1 ? OWNER : 0));
                }
            }
        }
        List<Wrote> wrote = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        writer.observe((l, tick, site, pos, state) -> {
            if (!writer.ownWrite().active()) {
                problems.add("a write at " + pos + " without OwnWrite");
            }
            if (site.match() == a.id().value() || site.match() == b.id().value()) {
                wrote.add(new Wrote(tick, site, pos.immutable()));
            }
        });
        for (Asked w : asked) {
            writer.enqueue(level, w.site(), WritePriority.CONSTRUCTION, w.pos(), w.state(), w.owner());
        }
        h.startSequence()
                .thenWaitUntil(() ->
                        h.assertTrue(wrote.size() >= asked.size(), wrote.size() + " of " + asked.size() + " written"))
                .thenExecute(() -> {
                    writer.observe(null);
                    check(h, problems.isEmpty(), String.join("; ", problems));
                    checkBudgetAndBursts(h, wrote);
                    checkOrder(h, asked, wrote);
                    for (Asked w : asked) {
                        check(h, level.getBlockState(w.pos()).is(Blocks.STONE), "no stone at " + w.pos());
                        check(
                                h,
                                writer.protectedBlocks(level).owner(w.pos()) == w.owner(),
                                w.pos() + " is protected by "
                                        + writer.protectedBlocks(level).owner(w.pos()) + ", not " + w.owner());
                        check(
                                h,
                                writer.protectedBlocks(level).matchOf(w.pos())
                                        == (w.owner() == 0 ? 0 : w.site().match()),
                                w.pos() + " is protected for match "
                                        + writer.protectedBlocks(level).matchOf(w.pos()));
                    }
                })
                .thenExecute(() -> {
                    StrategosServer.get().matches().dispose(a.id());
                    check(
                            h,
                            writer.protectedBlocks(level).owner(asked.get(0).pos()) == 0,
                            "a disposed match's blocks stay protected");
                })
                .thenSucceed();
    }

    private static void checkBudgetAndBursts(GameTestHelper h, List<Wrote> wrote) {
        Map<Long, Integer> perTick = new HashMap<>();
        Map<WriteSite, Long> lastBurst = new HashMap<>();
        for (Wrote w : wrote) {
            perTick.merge(w.tick(), 1, Integer::sum);
            Long last = lastBurst.put(w.site(), w.tick());
            if (last != null && last != w.tick() && w.tick() - last < WriteScheduler.BURST_INTERVAL) {
                h.fail(w.site() + " wrote on ticks " + last + " and " + w.tick());
            }
        }
        int most = perTick.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        check(h, most <= WriteScheduler.BUDGET, most + " writes in one tick");
        check(h, most == WriteScheduler.BUDGET, "saturated queues wrote at most " + most + " a tick");
    }

    /** The writer's order is the scheduler's: the same queues drained on the same ticks give the same writes. */
    private static void checkOrder(GameTestHelper h, List<Asked> asked, List<Wrote> wrote) {
        WriteScheduler<BlockPos> model = new WriteScheduler<>();
        for (Asked w : asked) {
            model.enqueue(w.site(), WritePriority.CONSTRUCTION, w.pos());
        }
        List<Wrote> expected = new ArrayList<>();
        for (long tick = wrote.get(0).tick(); model.pending() > 0; tick++) {
            long now = tick;
            model.drain(now, WriteScheduler.BUDGET, (site, pos) -> expected.add(new Wrote(now, site, pos)));
        }
        check(h, expected.equals(wrote), "the writer's order differs from the scheduler's");
    }

    private static void check(GameTestHelper h, boolean ok, String message) {
        if (!ok) {
            h.fail(message);
        }
    }
}
