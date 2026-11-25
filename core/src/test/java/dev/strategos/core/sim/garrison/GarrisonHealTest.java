package dev.strategos.core.sim.garrison;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.shared.Handle;
import org.junit.jupiter.api.Test;

/** BLD-10.1 in L1: a wounded unit in a civic centre gains the data's 1 HP a second, in steps of 20 ticks, to its most. */
class GarrisonHealTest {
    final GarrisonScene g = new GarrisonScene();

    @Test
    void aWoundedInfantryGainsOneHitPointEveryTwentyTicksAndStopsAtItsMost() {
        int centre = g.building(0, 40, 30);
        int unit = g.garrisoned(GarrisonScene.infantry(), 0, centre, 1)[0];
        int i = Handle.index(unit);
        assertThat(Garrisons.heal(g.s, centre)).isEqualTo(1);
        g.s.health().setDamage(i, 2.5);

        double previous = 2.5;
        int steps = 0;
        for (int t = 0; t < 20 * 3 && previous > 0; t++) {
            g.h.runTicks(1);
            double now = g.s.health().damage(i);
            if (now != previous) {
                assertThat(previous - now)
                        .as("one hit point a heal, never more")
                        .isEqualTo(Math.min(1, previous));
                assertThat(g.s.tick() % 20).as("on the heal's tick").isZero();
                steps++;
                previous = now;
            }
        }
        assertThat(steps).as("2.5 damage: 1, 1, then the last 0.5").isEqualTo(3);
        assertThat(g.s.health().damage(i)).isZero();
        g.h.runTicks(60);
        assertThat(g.s.health().damage(i)).as("never above its most").isZero();
        assertThat(g.inside(unit)).isTrue();
    }

    @Test
    void aUnitOutsideDoesNotHeal() {
        int[] out = g.units(GarrisonScene.infantry(), 0, 10.5, 10.5, 1);
        int i = Handle.index(out[0]);
        g.s.health().setDamage(i, 5);
        g.h.runTicks(60);
        assertThat(g.s.health().damage(i)).isEqualTo(5);
    }
}
