package dev.strategos.core.sim.territory;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.port.JobExecutor;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.TerritoryGrid;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.ThreadedJobExecutor;
import dev.strategos.core.testkit.VoxelFixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * TERR-01 (M9 spec §2.3): the territory system's cadence, its job delivery on 0, 1 and 4 threads, and its save. A
 * scene of two factions on a 256² flat map: two civic centres at the start, then houses and a third centre every few
 * ticks, some closer together than the cadence.
 */
class TerritorySystemTest {
    private static final TemplateId CENTRE = new TemplateId("strategos:building/civic_centre");
    private static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    private static final int TICKS = 120;

    @Test
    void theGridIsTheSameOnEveryTickWithZeroOneAndFourThreads() {
        List<Long> inline = run(JobExecutor.inline());
        try (ThreadedJobExecutor one = new ThreadedJobExecutor(1);
                ThreadedJobExecutor four = new ThreadedJobExecutor(4)) {
            assertThat(run(one)).isEqualTo(inline);
            assertThat(run(four)).isEqualTo(inline);
        }
    }

    @Test
    void aRecomputeStartsAtMostOncePerTwentyTicksAndDeliversOnTheNext() {
        SimHarness h = scene(JobExecutor.inline());
        MatchState s = KernelAccess.state(h.core());
        TerritorySystem t = Objects.requireNonNull(s.play().territorySystem());
        h.step(); // the centres land; the first grid is computed at once
        assertThat(t.recomputes()).isEqualTo(1);
        assertThat(t.grid().ownerAt(40, 40)).isZero();
        assertThat(t.grid().ownerAt(200, 200)).isEqualTo(1);
        assertThat(t.grid().ownerAt(40, 120))
                .as("beyond the centre's 56 blocks")
                .isEqualTo(-1);

        for (int k = 0; k < 10; k++) { // ten houses in one tick
            h.submit(PlayerSlot.ADMIN, new SpawnBuilding(HOUSE, new FactionSlot(0), 30 + 8 * k, 100, 0));
        }
        for (int i = 0; i < 25; i++) {
            h.step();
        }
        assertThat(t.recomputes())
                .as("ten completions in one tick, one recompute")
                .isEqualTo(2);
        assertThat(t.grid().ownerAt(40, 106)).isZero();
        assertThat(t.grid().connectedAt(40, 106))
                .as("joined to the centre's land")
                .isTrue();

        for (int i = 0; i < TerritorySystem.CADENCE; i++) {
            h.step();
        }
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(HOUSE, new FactionSlot(0), 120, 30, 0));
        h.step(); // starts at once: the last start was more than 20 ticks ago
        long started = h.tick();
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(HOUSE, new FactionSlot(0), 150, 30, 0));
        for (int i = 0; i < 4; i++) {
            h.step();
        }
        assertThat(t.recomputes()).isEqualTo(3);
        assertThat(t.grid().ownerAt(156, 36))
                .as("the second house waits for the cadence")
                .isEqualTo(-1);
        while (h.tick() < started + TerritorySystem.CADENCE + 1) {
            h.step();
        }
        assertThat(t.recomputes()).isEqualTo(4);
        assertThat(t.grid().ownerAt(156, 36)).isZero();
        assertThat(t.grid().connectedAt(156, 36))
                .as("a house's island is not connected")
                .isFalse();
    }

    @Test
    void aSaveMidRecomputeRestoresBothGridsAndTheSameFuture() {
        SimHarness live = scene(JobExecutor.inline());
        live.step();
        live.submit(PlayerSlot.ADMIN, new SpawnBuilding(HOUSE, new FactionSlot(0), 30, 100, 0));
        live.step(); // a recompute is pending now
        MatchSnapshot saved = live.core().snapshot();
        assertThat(saved.play().territory()).isNotEmpty();
        SimHarness restored = SimHarness.restore(saved, TemplateSet.builtin());
        for (int i = 0; i < 30; i++) {
            TerritoryGrid a = grid(live);
            TerritoryGrid b = grid(restored);
            assertThat(b).as("tick %d", live.tick()).isEqualTo(a);
            live.step();
            restored.step();
        }
        assertThat(grid(live).ownerAt(36, 106)).isZero();
    }

    private static TerritoryGrid grid(SimHarness h) {
        return Objects.requireNonNull(KernelAccess.state(h.core()).play().territorySystem())
                .grid();
    }

    private static List<Long> run(JobExecutor jobs) {
        SimHarness h = scene(jobs);
        List<Long> hashes = new ArrayList<>();
        for (int t = 0; t < TICKS; t++) {
            if (t % 7 == 3) {
                h.submit(PlayerSlot.ADMIN, new SpawnBuilding(HOUSE, new FactionSlot(t % 2), 20 + t, 110, 0));
            }
            if (t == 60) {
                h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, new FactionSlot(1), 120, 120, 0));
            }
            h.step();
            hashes.add(grid(h).hash());
        }
        return hashes;
    }

    private static SimHarness scene(JobExecutor jobs) {
        SimHarness h = SimHarness.builder()
                .size(256, 256)
                .yBand(0, 31, 1)
                .factions(2)
                .jobs(jobs)
                .build();
        h.publishAll(VoxelFixtures.flat(256, 256, 32, 1));
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, new FactionSlot(0), 30, 30, 0));
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, new FactionSlot(1), 200, 200, 0));
        return h;
    }
}
