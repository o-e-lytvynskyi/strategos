package dev.strategos.core.sim.vision;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

/** FOW-01: an enemy shows the tick it enters a seen cell and hides ten ticks after it was last seen. */
class VisibilityIndexTest {
    private final SimHarness h = SimHarness.builder().size(384, 384).factions(2).build();
    private final MatchState state = KernelAccess.state(h.core());

    @Test
    void revealAtOnceHideAfterTen() {
        h.spawn(new FactionSlot(0), new Vec2d(50, 50), 1, 0.0);
        int enemy = h.spawn(new FactionSlot(1), new Vec2d(200, 200), 1, 0.0)[0];
        VisibilityIndex seen = state.visionSystem().visibility(0);
        assertThat(seen.isVisible(enemy)).isFalse();

        place(enemy, 54, 50);
        h.step();
        assertThat(seen.isVisible(enemy)).isTrue();

        place(enemy, 200, 200);
        for (int tick = 1; tick <= VisibilityIndex.HYSTERESIS; tick++) {
            h.step();
            assertThat(seen.isVisible(enemy))
                    .as("tick %d after last seen", tick)
                    .isTrue();
        }
        h.step();
        assertThat(seen.isVisible(enemy)).isFalse();
    }

    private void place(int handle, double x, double z) {
        int i = Handle.index(handle);
        state.motion().set(i, x, state.motion().y(i), z, 0);
    }
}
