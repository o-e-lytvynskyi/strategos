package dev.strategos.core.sim.garrison;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.Footprint;
import dev.strategos.core.shared.Handle;
import org.junit.jupiter.api.Test;

/** BLD-10.2 in L1: a garrisoned building under 10 % of its health lets everyone out beside it, once; at 11 % they stay. */
class GarrisonEjectTest {
    final GarrisonScene g = new GarrisonScene();

    @Test
    void underTenPercentEveryoneComesOutBesideTheBuilding() {
        int centre = g.building(0, 40, 30);
        int[] units = g.garrisoned(GarrisonScene.infantry(), 0, centre, 3);
        double max = g.s.buildingMaxHp(centre);
        assertThat(Garrisons.ejectBelow(g.s, centre)).isEqualTo(0.1);

        g.s.buildings().setDamage(centre, max * 0.89);
        g.h.runTicks(2);
        assertThat(g.s.garrison().count(centre)).as("at 11 % they stay").isEqualTo(3);

        g.s.buildings().setDamage(centre, max * 0.91);
        g.h.runTicks(1);
        assertThat(g.s.garrison().count(centre)).as("at 9 % they are out").isZero();
        Footprint at = g.s.footprintOf(centre);
        for (int unit : units) {
            int i = Handle.index(unit);
            assertThat(g.inside(unit)).isFalse();
            double dx = Math.max(
                    Math.max(at.anchorX() - g.s.motion().x(i), g.s.motion().x(i) - (at.anchorX() + at.spanX())), 0);
            double dz = Math.max(
                    Math.max(at.anchorZ() - g.s.motion().z(i), g.s.motion().z(i) - (at.anchorZ() + at.spanZ())), 0);
            assertThat(Math.hypot(dx, dz)).as("standing next to the building").isLessThan(4);
        }
        assertThat(g.s.buildings().alive(centre)).isTrue();
    }
}
