package dev.strategos.core.sim.vision;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

class ExploredTest {

    @Test
    void neverReverts() {
        SimHarness h = SimHarness.builder().size(384, 384).factions(2).build();
        MatchState state = KernelAccess.state(h.core());
        int unit = h.spawn(new FactionSlot(0), new Vec2d(48, 48), 1, 4.0)[0];
        h.step();

        VisionGrid grid = state.visionSystem().grid(0);
        long[] before = snapshot(grid);
        assertExploredNotEmpty(before);

        state.entities().destroy(unit);
        h.runTicks(20);

        long[] after = snapshot(grid);
        for (int i = 0; i < before.length; i++) {
            assertThat(after[i] & before[i])
                    .as("explored word %d must keep every bit it had", i)
                    .isEqualTo(before[i]);
        }
    }

    private static long[] snapshot(VisionGrid grid) {
        java.util.List<Long> words = grid.saveExplored();
        long[] out = new long[words.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = words.get(i);
        }
        return out;
    }

    private static void assertExploredNotEmpty(long[] words) {
        long or = 0;
        for (long w : words) {
            or |= w;
        }
        assertThat(or).as("the unit must have explored at least one cell").isNotZero();
    }
}
