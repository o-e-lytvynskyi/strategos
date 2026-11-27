package dev.strategos.core.sim.formation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.FormationShape;
import dev.strategos.core.testkit.FakeAllies;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import org.junit.jupiter.api.Test;

/**
 * ORD-06 routes: the formation's one search crosses the gates its faction may (its own, its allies'; PATH-12) and not an
 * enemy's, and a route that stopped short is searched again on the grid of the most confined member.
 */
class FormationRouteTest {
    private static final int[] GATE = {60 << 16 | 29, 60 << 16 | 30};

    /** A wall across x 60 of a 120 by 60 world, its gate columns open to the sky until a gate is built there. */
    private static FormationFixtures walled(int owner, boolean allied) {
        VoxelWorld w = VoxelFixtures.flat(120, 60, 32, 1);
        w.fill(60, 1, 0, 60, 4, 59, VoxelFixtures.SOLID);
        w.fill(60, 1, 29, 60, 4, 30, VoxelFixtures.AIR);
        FormationFixtures f = FormationFixtures.flat(true, w);
        f.s.play().addGate(owner, GATE);
        if (allied) {
            f.s.setAllies(new FakeAllies(new int[] {0, 0}));
            f.s.play().gates().touch();
        }
        f.h.runTicks(3);
        return f;
    }

    private static double westmost(FormationFixtures f, int[] units) {
        double west = Double.MAX_VALUE;
        for (int u : units) {
            west = Math.min(west, f.x(u));
        }
        return west;
    }

    private static double eastmost(FormationFixtures f, int[] units) {
        double east = -Double.MAX_VALUE;
        for (int u : units) {
            east = Math.max(east, f.x(u));
        }
        return east;
    }

    @Test
    void aFormationCrossesItsOwnGate() {
        FormationFixtures f = walled(0, false);
        int[] units = f.block(TemplateSet.INFANTRY, 4, 2, 1.5, 40, 29.5);
        FormationController c = f.march(units, 85, 30, FormationShape.BOX);

        assertThat(f.h.runUntil(c::holding, 3000)).isTrue();
        f.h.runTicks(100);
        assertThat(westmost(f, units)).as("every member is through the gate").isGreaterThan(62);
    }

    @Test
    void aFormationCrossesAnAlliesGate() {
        FormationFixtures f = walled(1, true);
        int[] units = f.block(TemplateSet.INFANTRY, 4, 2, 1.5, 40, 29.5);
        FormationController c = f.march(units, 85, 30, FormationShape.BOX);

        assertThat(f.h.runUntil(c::holding, 3000)).isTrue();
        f.h.runTicks(100);
        assertThat(westmost(f, units)).as("every member is through the gate").isGreaterThan(62);
    }

    @Test
    void anEnemysGateKeepsTheFormationOut() {
        FormationFixtures f = walled(1, false);
        int[] units = f.block(TemplateSet.INFANTRY, 4, 2, 1.5, 40, 29.5);
        FormationController c = f.march(units, 85, 30, FormationShape.BOX);

        assertThat(f.h.runUntil(c::holding, 3000))
                .as("it walks as far as the wall and stands")
                .isTrue();
        f.h.runTicks(100);
        assertThat(eastmost(f, units)).as("nobody is across").isLessThan(60);
        assertThat(c.anchorEndX()).isLessThan(60);
    }

    @Test
    void aRouteThatStoppedShortIsSearchedAgainAndTheFormationGetsThere() {
        FormationFixtures f = FormationFixtures.flat(true);
        int[] units = f.block(TemplateSet.INFANTRY, 12, 4, 1.5, 20, 50);
        FormationController c = f.march(units, 60, 50, FormationShape.BOX);
        assertThat(f.h.runUntil(c::holding, 1500)).isTrue();

        c.setGoal(150, 50); // as if the first search had been cut short at (60, 50)
        c.setRoute(new double[] {c.anchorEndX(), c.anchorEndZ(), c.anchorEndX() + 1, c.anchorEndZ()}, true);
        c.setHolding(false);

        assertThat(f.h.runUntil(c::holding, 3000)).isTrue();
        assertThat(c.anchorEndX()).as("the second search went on to the goal").isGreaterThan(148);
        assertThat(eastmost(f, units)).isGreaterThan(145);
    }

    /** A world cut by a wall at x 100 with a door two blocks high at z 48..52: spearmen pass it, horsemen do not. */
    private static VoxelWorld lowDoor() {
        VoxelWorld w = VoxelFixtures.flat(160, 100, 32, 1);
        w.fill(100, 1, 0, 100, 10, 99, VoxelFixtures.SOLID);
        w.fill(100, 1, 48, 100, 2, 52, VoxelFixtures.AIR);
        return w;
    }

    private static void searchAgain(FormationController c, double goalX) {
        c.setGoal(goalX, 50);
        c.setRoute(new double[] {c.anchorEndX(), c.anchorEndZ(), c.anchorEndX() + 1, c.anchorEndZ()}, true);
        c.setHolding(false);
    }

    @Test
    void theControlSpearmenAloneGoThroughTheLowDoorOnASecondSearch() {
        FormationFixtures f = FormationFixtures.flat(true, lowDoor());
        int[] spears = f.block(TemplateSet.INFANTRY, 8, 4, 1.5, 30, 50);
        FormationController c = f.march(spears, 90, 50, FormationShape.BOX);
        assertThat(f.h.runUntil(c::holding, 2500)).isTrue();

        searchAgain(c, 140);

        assertThat(f.h.runUntil(c::holding, 3000)).isTrue();
        assertThat(c.anchorEndX()).isGreaterThan(138);
    }

    @Test
    void aRouteSearchedAgainUsesTheMostConfinedMembersGrid() {
        FormationFixtures f = FormationFixtures.flat(true, lowDoor());
        int[] horse = f.block(FormationFixtures.HORSEMAN, 1, 1, 3, 20, 50);
        int[] spears = f.block(TemplateSet.INFANTRY, 7, 4, 1.5, 36, 50);
        int[] units = FormationFixtures.both(horse, spears);
        FormationController c = f.march(units, 90, 50, FormationShape.BOX);
        assertThat(f.h.runUntil(c::holding, 2500)).isTrue();

        searchAgain(c, 140);
        f.h.runTicks(1500);

        assertThat(c.holding()).isTrue();
        assertThat(c.anchorEndX())
                .as("the horseman's grid has no way through the door")
                .isLessThan(100);
    }
}
