package dev.strategos.core.sim.world;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.sim.WorldEffect;
import dev.strategos.core.sim.nav.BlockClass;
import dev.strategos.core.testkit.FakeWorld;
import dev.strategos.core.testkit.VoxelWorld;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * BLD-15.3: {@code FakeWorld} applies effects in {@link WriteScheduler}'s order under its budget, the order the mod's
 * {@code BlockWriter} uses: at most the budget a tick, sites round-robin from the one after the last served, a site at
 * most every 4 ticks, priorities then first in first out inside a site.
 */
class FakeWorldOrderTest {
    private static FakeWorld world() {
        return new FakeWorld(
                new VoxelWorld(64, 16, 64),
                0,
                state -> state.equals(WorldEffect.AIR) ? BlockClass.AIR : BlockClass.SOLID);
    }

    private static WorldEffect place(int site, WritePriority priority, int i) {
        return new WorldEffect(site, priority, i % 64, 1 + i / 64 % 15, site, "minecraft:stone", 0);
    }

    @Test
    void theBudgetHoldsAndASiteWritesAtMostEveryFourTicks() {
        FakeWorld world = world();
        for (int match = 1; match <= 2; match++) {
            for (int site = 1; site <= 3; site++) {
                for (int i = 0; i < 150; i++) {
                    world.submit(match, place(site, WritePriority.CONSTRUCTION, i));
                }
            }
        }
        int ticks = 0;
        while (world.pending() > 0) {
            world.tick(WriteScheduler.BUDGET);
            ticks++;
        }

        Map<Long, Integer> perTick = new HashMap<>();
        Map<WriteSite, List<Long>> burstsOf = new HashMap<>();
        for (FakeWorld.Applied a : world.log()) {
            perTick.merge(a.tick(), 1, Integer::sum);
            List<Long> bursts = burstsOf.computeIfAbsent(a.site(), k -> new ArrayList<>());
            if (bursts.isEmpty() || bursts.get(bursts.size() - 1) != a.tick()) {
                bursts.add(a.tick());
            }
        }
        assertThat(world.log()).hasSize(900);
        assertThat(perTick.values()).allMatch(n -> n <= WriteScheduler.BUDGET);
        assertThat(perTick.values().stream().mapToInt(Integer::intValue).max().orElse(0))
                .as("saturated queues use the whole budget")
                .isEqualTo(WriteScheduler.BUDGET);
        assertThat(burstsOf).hasSize(6);
        for (List<Long> bursts : burstsOf.values()) {
            for (int i = 1; i < bursts.size(); i++) {
                assertThat(bursts.get(i) - bursts.get(i - 1)).isGreaterThanOrEqualTo(WriteScheduler.BURST_INTERVAL);
            }
        }
        assertThat(ticks).as("900 writes at 64 a tick take at least 15 ticks").isGreaterThanOrEqualTo(15);
    }

    @Test
    void sitesTakeTurnsFromTheOneAfterTheLastServed() {
        FakeWorld world = world();
        for (int site = 1; site <= 3; site++) {
            for (int i = 0; i < 100; i++) {
                world.submit(1, place(site, WritePriority.CONSTRUCTION, i));
            }
        }
        world.tick(64);
        world.tick(64);
        world.tick(64);

        List<FakeWorld.Applied> log = world.log();
        assertThat(log.subList(0, 64)).allMatch(a -> a.site().equals(new WriteSite(1, 1)));
        assertThat(log.subList(64, 128)).allMatch(a -> a.site().equals(new WriteSite(1, 2)));
        assertThat(log.subList(128, 192)).allMatch(a -> a.site().equals(new WriteSite(1, 3)));
        world.tick(64);
        assertThat(world.log())
                .as("tick 3: every site wrote within the last 4 ticks")
                .hasSize(192);
        world.tick(64);
        world.tick(64);
        world.tick(64);

        assertThat(world.log()).hasSize(300);
        for (int site = 1; site <= 3; site++) {
            int index = 192 + (site - 1) * 36;
            assertThat(world.log().subList(index, index + 36))
                    .as("site %d writes its last 36 on tick %d, 4 ticks after its first burst", site, 3 + site)
                    .allMatch(a -> a.tick() == 3 + a.site().site());
        }
    }

    @Test
    void insideASitePrioritiesComeFirstThenArrival() {
        FakeWorld world = world();
        world.submit(1, place(7, WritePriority.COSMETIC, 0));
        world.submit(1, place(7, WritePriority.IMPORT, 1));
        world.submit(1, place(7, WritePriority.CONSTRUCTION, 2));
        world.submit(1, place(7, WritePriority.DAMAGE_STAGE, 3));
        world.submit(1, place(7, WritePriority.NODE_REMOVAL, 4));
        world.submit(1, place(7, WritePriority.CONSTRUCTION, 5));

        world.tick(64);

        assertThat(world.log()).extracting(a -> a.effect().x()).containsExactly(4, 2, 5, 3, 0, 1);
    }

    @Test
    void theSameEffectsGiveTheSameWritesWhicheverSiteWasSubmittedFirst() {
        FakeWorld a = world();
        FakeWorld b = world();
        for (int i = 0; i < 80; i++) {
            a.submit(1, place(1, WritePriority.CONSTRUCTION, i));
            a.submit(2, place(4, WritePriority.CONSTRUCTION, i));
        }
        for (int i = 0; i < 80; i++) {
            b.submit(2, place(4, WritePriority.CONSTRUCTION, i));
        }
        for (int i = 0; i < 80; i++) {
            b.submit(1, place(1, WritePriority.CONSTRUCTION, i));
        }
        while (a.pending() > 0 || b.pending() > 0) {
            a.tick(32);
            b.tick(32);
        }

        assertThat(b.log()).isEqualTo(a.log());
    }

    @Test
    void droppingAMatchForgetsOnlyItsWrites() {
        WriteScheduler<Integer> scheduler = new WriteScheduler<>();
        for (int i = 0; i < 10; i++) {
            scheduler.enqueue(new WriteSite(1, 1), WritePriority.CONSTRUCTION, i);
            scheduler.enqueue(new WriteSite(2, 1), WritePriority.CONSTRUCTION, 100 + i);
        }

        scheduler.drop(1);

        assertThat(scheduler.pending()).isEqualTo(10);
        assertThat(scheduler.pending(new WriteSite(1, 1))).isZero();
        List<Integer> written = new ArrayList<>();
        scheduler.drain(0, 64, (site, write) -> written.add(write));
        assertThat(written).allMatch(w -> w >= 100).hasSize(10);
    }

    @Test
    void aSiteThatEmptiesAndRefillsStillWaitsItsTurn() {
        FakeWorld world = world();
        world.submit(1, place(1, WritePriority.CONSTRUCTION, 0));
        assertThat(world.tick(64)).containsExactly(new WriteSite(1, 1));
        world.submit(1, place(1, WritePriority.CONSTRUCTION, 1));

        world.tick(64);
        world.tick(64);
        world.tick(64);
        assertThat(world.log()).hasSize(1);
        world.tick(64);

        assertThat(world.log()).hasSize(2);
        assertThat(world.log().get(1).tick()).isEqualTo(4);
        assertThat(world.voxels().get(1, 1, 1)).isEqualTo(BlockClass.SOLID);
    }
}
