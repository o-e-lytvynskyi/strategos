package dev.strategos.core.sim.formation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.FormationShape;
import dev.strategos.core.protocol.SetStance;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Stance;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.TerrainPage;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.nav.MobilityClass;
import dev.strategos.core.sim.nav.PageBuilder;
import dev.strategos.core.sim.nav.PageCoord;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import org.junit.jupiter.api.Test;

/**
 * ORD-06 on the march: a wall built across the route, members that look for enemies only once the formation stands, a
 * member that cannot reach its place not holding the march for ever, and the slot error through a turn of 90 degrees
 * and more.
 */
class FormationMarchTest {
    private static final PlayerSlot P0 = PlayerSlot.ADMIN;

    private static int passiveEnemy(FormationFixtures f, double x, double z) {
        f.h.submit(P0, new SpawnUnits(TemplateSet.INFANTRY, new FactionSlot(1), new Vec2d(x, z), 1, 0));
        int enemy = f.h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .findFirst()
                .orElseThrow();
        f.h.submit(P0, new SetStance(UnitSet.of(enemy), Stance.PASSIVE));
        f.h.step();
        return enemy;
    }

    @Test
    void aWallBuiltAcrossTheRouteSendsTheMembersAroundAndTheFormationArrives() {
        VoxelWorld world = VoxelFixtures.flat(200, 120, 32, 1);
        FormationFixtures f = FormationFixtures.flat(true, world);
        int[] units = f.block(TemplateSet.INFANTRY, 12, 4, 1.5, 20, 48);
        FormationController c = f.march(units, 150, 50, FormationShape.BOX);
        f.h.runTicks(120);
        assertThat(c.advancing()).isTrue();

        world.fill(100, 1, 40, 100, 3, 60, VoxelFixtures.SOLID); // a wall, 21 long, across the way
        PageBuilder builder = new PageBuilder(MobilityClass.INFANTRY);
        for (int pz = 40 >> 4; pz <= 60 >> 4; pz++) {
            PageCoord coord = new PageCoord(100 >> 4, pz);
            f.h.publish(TerrainPage.build(coord, world.slice(coord), builder));
        }

        assertThat(f.h.runUntil(c::holding, 6000)).as("the formation arrives").isTrue();
        f.h.runTicks(200);
        double worst = 0;
        for (int k = 0; k < c.size(); k++) {
            worst = Math.max(worst, f.error(c, k));
            assertThat(f.x(c.member(k))).as("member %d is across the wall", k).isGreaterThan(120);
        }
        assertThat(c.size()).as("nobody was lost on the way round").isEqualTo(12);
        assertThat(worst).as("every member stands on its place").isLessThan(1.5);
    }

    @Test
    void membersWatchForEnemiesOnlyOnceTheFormationStands() {
        FormationFixtures f = FormationFixtures.flat(false);
        int[] units = f.block(TemplateSet.INFANTRY, 8, 4, 1.5, 20, 48);
        FormationController c = f.march(units, 120, 50, FormationShape.BOX);
        int enemy = passiveEnemy(f, 70, 54);

        int ticks = 0;
        while (c.anchorX() < 100 && ticks++ < 1200) {
            f.h.step();
            assertThat(c.size())
                    .as("no member left the march at %d", f.h.tick())
                    .isEqualTo(8);
            assertThat(f.s.orders().kind(Handle.index(c.member(0)))).isEqualTo(OrderKind.FORMATION);
        }
        assertThat(c.anchorX()).as("the formation passed the enemy").isGreaterThanOrEqualTo(100);

        assertThat(f.h.runUntil(c::holding, 400)).isTrue();
        FormationFixtures standing = FormationFixtures.flat(false);
        int[] others = standing.block(TemplateSet.INFANTRY, 8, 4, 1.5, 20, 48);
        FormationController held = standing.march(others, 60, 50, FormationShape.BOX);
        assertThat(standing.h.runUntil(held::holding, 800)).isTrue();
        standing.h.runTicks(30);
        assertThat(held.size()).as("standing alone, nobody leaves").isEqualTo(8);
        int foe = passiveEnemy(standing, 66, 52);

        assertThat(standing.h.runUntil(() -> held.size() < 8, 60))
                .as("a member sees the enemy and goes for it")
                .isTrue();
        boolean engaged = false;
        for (int u : others) {
            engaged |= standing.s.orders().kind(Handle.index(u)) == OrderKind.ENGAGE
                    && standing.s.orders().target(Handle.index(u)) == foe;
        }
        assertThat(engaged).isTrue();
        assertThat(enemy).isPositive();
    }

    @Test
    void aMemberThatCannotReachItsPlaceHoldsTheMarchForNoMoreThanAHundredTicks() {
        VoxelWorld world = VoxelFixtures.flat(200, 120, 32, 1);
        world.fill(30, 1, 80, 34, 3, 84, VoxelFixtures.SOLID);
        world.fill(31, 1, 81, 33, 3, 83, VoxelFixtures.AIR); // a pen
        FormationFixtures f = FormationFixtures.flat(true, world);
        int[] units = f.block(TemplateSet.INFANTRY, 8, 4, 1.5, 20, 48);
        FormationController c = f.march(units, 150, 50, FormationShape.BOX);
        f.h.runTicks(40);
        f.s.motion().set(Handle.index(units[0]), 32.5, 1, 82.5, 0); // one is carried into the pen

        double along = c.along();
        f.h.runTicks(80);
        assertThat(c.waited()).as("it is waiting for the one in the pen").isBetween(60, 99);
        double stopped = c.along();
        assertThat(stopped).as("and barely moved while it waited").isLessThan(along + 15);

        f.h.runTicks(120);

        assertThat(c.waited()).as("the wait is over").isEqualTo(FormationSystem.WAIT_MAX);
        assertThat(c.advancing()).as("the march goes on without it").isTrue();
        assertThat(c.along()).isGreaterThan(stopped + 10);
    }

    /** The march of sixteen along {@code route} (x, z pairs from the anchor), the slot errors of the ticks it walked. */
    private static void corner(String name, double[] turnAt, FormationShape shape, double maxAllowed) {
        FormationFixtures f = FormationFixtures.flat(true);
        int[] units = f.block(TemplateSet.INFANTRY, 16, 4, 1.5, 20, 50);
        FormationController c = f.march(units, 60, 50, shape);
        assertThat(f.h.runUntil(c::holding, 1500)).isTrue();
        double x = c.anchorEndX();
        double z = c.anchorEndZ();
        c.setRoute(new double[] {x, z, turnAt[0], z, turnAt[0], turnAt[1]}, false);
        c.setHolding(false);

        double max = 0;
        double sum = 0;
        long samples = 0;
        for (int t = 0; t < 3000 && !c.holding(); t++) {
            f.h.step();
            if (c.advancing()) {
                for (int k = 0; k < c.size(); k++) {
                    double e = f.error(c, k);
                    max = Math.max(max, e);
                    sum += e;
                    samples++;
                }
            }
        }
        System.out.printf(
                "FORMATION-CORNER-ERROR %s shape=%s max=%.3f mean=%.3f arrived-at=%d%n",
                name, shape, max, sum / samples, f.h.tick());
        assertThat(c.holding()).isTrue();
        assertThat(c.size()).isEqualTo(16);
        assertThat(max).as("the largest slot error through the turn").isLessThan(maxAllowed);
    }

    @Test
    void slotErrorThroughATurnOfNinetyDegreesStaysUnderABlock() {
        corner("right-turn", new double[] {120, 10}, FormationShape.BOX, 1.0);
        corner("left-turn", new double[] {120, 90}, FormationShape.BOX, 1.0);
        corner("right-turn", new double[] {120, 10}, FormationShape.LINE, 1.0);
    }

    /**
     * A U round the end of a long wall, the way string pulling draws it, the corner at the wall's end: the formation's
     * places beside the corner fall inside the wall, so its members cannot hold them (reported, not bounded by a block).
     */
    @Test
    void roundTheEndOfAWallTheFormationStillGetsThereWithNobodyLost() {
        VoxelWorld world = VoxelFixtures.flat(170, 100, 32, 1);
        world.fill(0, 1, 40, 100, 4, 43, VoxelFixtures.SOLID);
        FormationFixtures f = FormationFixtures.flat(true, world);
        int[] units = f.block(TemplateSet.INFANTRY, 16, 4, 1.5, 30, 44.5);
        FormationController c = f.march(units, 30, 20, FormationShape.BOX);

        double max = 0;
        for (int t = 0; t < 4000 && !c.holding(); t++) {
            f.h.step();
            if (c.advancing()) {
                for (int k = 0; k < c.size(); k++) {
                    max = Math.max(max, f.error(c, k));
                }
            }
        }
        System.out.printf("FORMATION-CORNER-ERROR wall-end max=%.3f arrived-at=%d%n", max, f.h.tick());
        assertThat(c.holding()).isTrue();
        assertThat(c.size()).isEqualTo(16);
    }
}
