package dev.strategos.core.sim.vision;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.testkit.SimHarness;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** FOW-01: the vision grid's counts are what a brute-force pass over every unit and cell gives. */
class VisionGridTest {
    private static final int SIZE = 384;

    private final SimHarness h =
            SimHarness.builder().size(SIZE, SIZE).factions(2).build();
    private final MatchState state = KernelAccess.state(h.core());

    @Test
    void equalsBruteForce() {
        Random random = new Random(42);
        int[] units = new int[200];
        for (int f = 0; f < 2; f++) {
            int[] spawned = h.spawn(new FactionSlot(f), new Vec2d(192, 192), 100, 0.0);
            System.arraycopy(spawned, 0, units, f * 100, 100);
        }
        for (int unit : units) {
            int i = Handle.index(unit);
            state.motion().set(i, random.nextDouble() * SIZE, state.motion().y(i), random.nextDouble() * SIZE, 0);
        }
        h.step();

        for (int f = 0; f < 2; f++) {
            VisionGrid grid = state.visionSystem().grid(f);
            for (int cz = 0; cz < grid.cellsZ(); cz++) {
                for (int cx = 0; cx < grid.cellsX(); cx++) {
                    assertThat(grid.count(cx, cz))
                            .as("faction %d cell %d,%d", f, cx, cz)
                            .isEqualTo(bruteForce(f, cx, cz));
                }
            }
        }
    }

    private int bruteForce(int faction, int cx, int cz) {
        int n = 0;
        for (int i = 0; i < state.entities().highWater(); i++) {
            if (!state.entities().aliveAt(i) || state.identity().faction(i) != faction) {
                continue;
            }
            int r = (int) (state.stats().vision(i) / DiscOffsets.CELL);
            int dx = cx - VisionGrid.cellOf(state.motion().x(i));
            int dz = cz - VisionGrid.cellOf(state.motion().z(i));
            if (dx * dx + dz * dz <= r * r) {
                n++;
            }
        }
        return n;
    }

    @Test
    void anObserverAddsNothing() {
        h.spawn(new FactionSlot(0), new Vec2d(100, 100), 3, 2.0);
        h.step();
        VisionGrid grid = state.visionSystem().grid(0);
        int[] before = counts(grid);

        h.spawn(new FactionSlot(2), new Vec2d(100, 100), 2, 2.0);
        h.step();

        assertThat(counts(grid)).containsExactly(before);
        assertThat(counts(state.visionSystem().grid(1))).containsOnly(0);
    }

    private static int[] counts(VisionGrid grid) {
        int[] out = new int[grid.cellsX() * grid.cellsZ()];
        for (int z = 0; z < grid.cellsZ(); z++) {
            for (int x = 0; x < grid.cellsX(); x++) {
                out[z * grid.cellsX() + x] = grid.count(x, z);
            }
        }
        return out;
    }
}
