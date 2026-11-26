package dev.strategos.core.sim.vision;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.DayClock;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.PlayRules;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

/**
 * MATCH-16.2: a building with {@code Vision/light} keeps that radius at night when it is larger than the scaled range
 * (a torch on a house, a campfire on the centre, the towers' beacons); one without it sees the scaled range only.
 */
class LightSourceTest {
    private static final int NIGHT = DayClock.NIGHT_FROM - DayClock.START;
    private static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    private static final TemplateId FIELD = new TemplateId("strategos:building/field");
    private static final TemplateId TOWER = new TemplateId("strategos:building/tower");

    @Test
    void aLitBuildingKeepsItsLightAtNightAndAnUnlitOneSeesTheScaledRange() {
        SimHarness h = SimHarness.builder()
                .size(256, 256)
                .factions(2)
                .rules(MatchRules.DEFAULTS.withPlay(PlayRules.DEFAULTS.withDayNight(true)))
                .build();
        MatchState s = KernelAccess.state(h.core());
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(HOUSE, new FactionSlot(0), 40, 40, 0));
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(FIELD, new FactionSlot(0), 140, 40, 0));
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(TOWER, new FactionSlot(0), 40, 140, 0));
        h.step();
        VisionGrid grid = s.visionSystem().grid(0);
        int[] at = new int[3];
        for (int handle : s.buildings().handles()) {
            at[
                    s.buildings().anchorX(handle) < 100 && s.buildings().anchorZ(handle) < 100
                            ? 0
                            : s.buildings().anchorX(handle) > 100 ? 1 : 2] = handle;
        }
        int house = at[0];
        int field = at[1];
        int tower = at[2];
        int hx = VisionGrid.cellOf(s.buildings().anchorX(house));
        int hz = VisionGrid.cellOf(s.buildings().anchorZ(house));
        int fx = VisionGrid.cellOf(s.buildings().anchorX(field));
        int fz = VisionGrid.cellOf(s.buildings().anchorZ(field));
        int tx = VisionGrid.cellOf(s.buildings().anchorX(tower));
        int tz = VisionGrid.cellOf(s.buildings().anchorZ(tower));

        assertThat(grid.count(hx + 3, hz))
                .as("by day a house sees its 12: three cells")
                .isPositive();
        assertThat(grid.count(hx + 4, hz)).isZero();
        assertThat(grid.count(fx + 3, fz)).isPositive();
        assertThat(grid.count(tx + 6, tz))
                .as("a tower sees 24 by day: six cells")
                .isPositive();

        h.runTicks(NIGHT - (int) s.tick());
        assertThat(grid.count(hx + 2, hz))
                .as("night: the house's torch holds 8 blocks, two cells")
                .isPositive();
        assertThat(grid.count(hx + 3, hz)).isZero();
        assertThat(grid.count(fx + 1, fz))
                .as("an unlit field sees 7.2: one cell")
                .isPositive();
        assertThat(grid.count(fx + 2, fz)).isZero();
        assertThat(grid.count(tx + 6, tz)).as("a tower's beacon holds all 24").isPositive();
        assertThat(grid.count(tx + 7, tz)).isZero();
    }

    @Test
    void theLightsAreContentWhichTheDefaultCivilisationShips() {
        var set = dev.strategos.core.content.TemplateSet.builtin();
        int light = set.valuePaths().byPath("Vision/light").id();
        int lit = 0;
        for (var t : set.all()) {
            if (!Double.isNaN(set.value(t.netIndex(), light))) {
                lit++;
                assertThat(t.isBuilding()).isTrue();
            }
        }
        assertThat(lit)
                .as("the centre, the house, the tower and the sentry tower")
                .isEqualTo(4);
    }
}
