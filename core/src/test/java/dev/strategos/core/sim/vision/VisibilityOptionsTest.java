package dev.strategos.core.sim.vision;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.PlayRules;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

/**
 * MATCH-07.1: Fog, Explored and Revealed each initialise the vision grids as specified at the match's start.
 */
class VisibilityOptionsTest {

    private static final int SIZE = 64;

    @Test
    void eachOptionInitialisesTheGrids() {
        fogLeavesDistantCellsDark();
        exploredMarksEveryCellWithoutSeeingThem();
        revealedSeesAndExploresEveryCell();
    }

    private static void fogLeavesDistantCellsDark() {
        SimHarness h = harness(PlayRules.DEFAULTS);
        MatchState state = KernelAccess.state(h.core());
        h.spawn(new FactionSlot(0), new Vec2d(8, 8), 1, 0);
        h.step();

        VisionGrid grid = state.visionSystem().grid(0);
        assertThat(grid.isExplored(VisionGrid.cellOf(8), VisionGrid.cellOf(8)))
                .as("fog: the unit's cell is explored")
                .isTrue();
        assertThat(grid.count(VisionGrid.cellOf(8), VisionGrid.cellOf(8)))
                .as("fog: the unit's cell is visible")
                .isGreaterThan(0);
        int far = VisionGrid.cellOf(SIZE - 4);
        assertThat(grid.isExplored(far, far))
                .as("fog: a distant cell stays unexplored")
                .isFalse();
        assertThat(grid.count(far, far))
                .as("fog: a distant cell is not visible")
                .isZero();
    }

    private static void exploredMarksEveryCellWithoutSeeingThem() {
        SimHarness h = harness(new PlayRules(PlayRules.Victory.NONE, false, true, false));
        MatchState state = KernelAccess.state(h.core());
        h.spawn(new FactionSlot(0), new Vec2d(8, 8), 1, 0);
        h.step();

        VisionGrid grid = state.visionSystem().grid(0);
        assertEveryCellExplored(grid, "explored");
        assertThat(grid.count(VisionGrid.cellOf(8), VisionGrid.cellOf(8)))
                .as("explored: vision still stamps the unit")
                .isGreaterThan(0);
        int far = VisionGrid.cellOf(SIZE - 4);
        assertThat(grid.count(far, far))
                .as("explored: a distant cell is not visible until a viewer reaches it")
                .isZero();
    }

    private static void revealedSeesAndExploresEveryCell() {
        SimHarness h = harness(new PlayRules(PlayRules.Victory.NONE, true, false, false));
        MatchState state = KernelAccess.state(h.core());
        h.step();

        VisionGrid grid = state.visionSystem().grid(0);
        assertEveryCellExplored(grid, "revealed");
        for (int z = 0; z < grid.cellsZ(); z++) {
            for (int x = 0; x < grid.cellsX(); x++) {
                assertThat(grid.count(x, z))
                        .as("revealed: cell %d,%d is visible", x, z)
                        .isGreaterThan(0);
            }
        }
    }

    private static SimHarness harness(PlayRules play) {
        return SimHarness.builder()
                .size(SIZE, SIZE)
                .factions(2)
                .rules(MatchRules.DEFAULTS.withPlay(play))
                .build();
    }

    private static void assertEveryCellExplored(VisionGrid grid, String option) {
        for (int z = 0; z < grid.cellsZ(); z++) {
            for (int x = 0; x < grid.cellsX(); x++) {
                assertThat(grid.isExplored(x, z))
                        .as("%s: cell %d,%d is explored", option, x, z)
                        .isTrue();
            }
        }
    }
}
