package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.testkit.RecordingMetrics;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

class PhaseOrderTest {

    @Test
    void everyTickRunsThePhasesZeroToEightInOrder() {
        RecordingMetrics metrics = new RecordingMetrics();
        SimHarness h = SimHarness.builder().metrics(metrics).build();
        int[] units = h.spawn(new FactionSlot(0), new Vec2d(8, 8), 3, 2.0);
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(units), new Vec2d(20, 8)));
        h.runTicks(20);

        for (long tick = 1; tick <= h.tick(); tick++) {
            assertThat(metrics.phasesOf(tick)).as("tick %d", tick).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8);
        }
    }
}
