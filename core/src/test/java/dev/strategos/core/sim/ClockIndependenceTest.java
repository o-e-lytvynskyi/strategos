package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.port.Clock;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

/** The simulation never reads the clock: a clock ten times slower must not change a single state (CORE-02.4). */
class ClockIndependenceTest {

    private static Clock advancingBy(long nanosPerRead) {
        long[] now = {0};
        return () -> now[0] += nanosPerRead;
    }

    @Test
    void aSlowerClockLeavesEveryStateEqual() {
        SimHarness fast = SimHarness.builder().clock(advancingBy(1_000_000)).build();
        SimHarness slow = SimHarness.builder().clock(advancingBy(10_000_000)).build();
        int[] fastUnits = fast.spawn(new FactionSlot(0), new Vec2d(16, 16), 20, 8.0);
        int[] slowUnits = slow.spawn(new FactionSlot(0), new Vec2d(16, 16), 20, 8.0);
        assertThat(slowUnits).containsExactly(fastUnits);

        for (int tick = 0; tick < 1000; tick++) {
            if (tick % 50 == 0) {
                Vec2d target = new Vec2d(2 + (tick / 50 * 7) % 28, 2 + (tick / 50 * 11) % 28);
                fast.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(fastUnits), target));
                slow.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(slowUnits), target));
            }
            fast.step();
            slow.step();
            assertThat(slow.stateHash()).as("tick %d", fast.tick()).isEqualTo(fast.stateHash());
        }
        assertThat(slow.core().timers().lastStepNanos())
                .isGreaterThan(fast.core().timers().lastStepNanos());
    }
}
