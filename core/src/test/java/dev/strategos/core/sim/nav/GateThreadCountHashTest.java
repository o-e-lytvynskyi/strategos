package dev.strategos.core.sim.nav;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.port.JobExecutor;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.ThreadedJobExecutor;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import org.junit.jupiter.api.Test;

/** PATH-12 and PATH-16.1: a match with a gate and an order across it hashes the same on 0, 1 and 4 path threads. */
class GateThreadCountHashTest {
    private static final int TICKS = 160;

    @Test
    void aCrossingOrderHashesTheSameOnZeroOneAndFourThreads() {
        Run inline = run(JobExecutor.inline());
        try (ThreadedJobExecutor one = new ThreadedJobExecutor(1);
                ThreadedJobExecutor four = new ThreadedJobExecutor(4)) {
            assertThat(run(one).hashes).isEqualTo(inline.hashes);
            assertThat(run(four).hashes).isEqualTo(inline.hashes);
        }
        assertThat(inline.pathsFound).as("the order's path crosses the gate").isPositive();
    }

    private static Run run(JobExecutor jobs) {
        VoxelWorld w = VoxelFixtures.flat(96, 96, 16, 1);
        w.fill(40, 1, 0, 40, 3, 95, VoxelFixtures.SOLID);
        int[] columns = {40 << 16 | 46, 40 << 16 | 47, 40 << 16 | 48, 40 << 16 | 49};
        for (int c : columns) {
            w.fill(40, 1, c & 0xFFFF, 40, 3, c & 0xFFFF, VoxelFixtures.AIR);
        }
        SimHarness h = SimHarness.builder().size(96, 96).factions(2).jobs(jobs).build();
        h.publishAll(w);
        h.step();
        KernelAccess.state(h.core()).play().addGate(0, columns);
        int[] units = h.spawn(new FactionSlot(0), new Vec2d(30, 48), 4, 2);
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(units), new Vec2d(60.5, 48.5)));
        long[] hashes = new long[TICKS];
        for (int t = 0; t < TICKS; t++) {
            h.step();
            hashes[t] = h.stateHash();
        }
        return new Run(hashes, h.queries().pathStats().ok());
    }

    private record Run(long[] hashes, long pathsFound) {}
}
