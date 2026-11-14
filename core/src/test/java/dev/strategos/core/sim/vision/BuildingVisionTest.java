package dev.strategos.core.sim.vision;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.stats.Modifier;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

/**
 * A building sees by its faction's value of {@code Vision/range}, modifiers included, and is stamped again when that
 * value moves (TECH-02's Town bonus reaches buildings through the same path); one that goes away stops seeing.
 */
class BuildingVisionTest {
    private static final TemplateId HOUSE = new TemplateId("strategos:building/house");

    @Test
    void aBuildingSeesByItsFactionsValueAndFollowsAModifier() {
        SimHarness h = SimHarness.builder().size(384, 384).factions(2).build();
        MatchState state = KernelAccess.state(h.core());
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(HOUSE, new FactionSlot(0), 100, 100, 0));
        h.step();
        int house = state.buildings().handles()[0];
        VisionGrid grid = state.visionSystem().grid(0);
        int cx = VisionGrid.cellOf(state.buildings().anchorX(house));
        int cz = VisionGrid.cellOf(state.buildings().anchorZ(house));
        assertThat(grid.count(cx, cz)).as("the house sees its own cell").isPositive();
        assertThat(grid.count(cx + 5, cz))
                .as("12 blocks reach three cells, not five")
                .isZero();

        state.stats().add(new FactionSlot(0), Modifier.add("Vision/range", 12, 9_001));
        h.step();
        assertThat(grid.count(cx + 5, cz)).as("24 blocks reach six cells").isPositive();
        assertThat(grid.isExplored(cx + 5, cz)).isTrue();

        state.stats().remove(new FactionSlot(0), 9_001);
        h.step();
        assertThat(grid.count(cx + 5, cz)).as("back to three cells").isZero();
        assertThat(grid.count(cx, cz)).isPositive();

        state.buildings().remove(house);
        h.step();
        assertThat(grid.count(cx, cz)).as("a building gone sees nothing").isZero();
        assertThat(grid.isExplored(cx, cz)).as("explored stays").isTrue();
    }
}
