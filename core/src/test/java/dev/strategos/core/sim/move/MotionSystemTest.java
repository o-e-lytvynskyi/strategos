package dev.strategos.core.sim.move;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.strategos.core.content.CorePaths;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.TerrainPage;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.nav.MobilityClass;
import dev.strategos.core.sim.nav.PageBuilder;
import dev.strategos.core.sim.nav.PageCoord;
import dev.strategos.core.sim.stats.Modifier;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import org.junit.jupiter.api.Test;

/**
 * PATH-05.1 (spec §5.8): on terrain a 1-block step takes exactly two ticks and Y ends on the floor; drops fall half a
 * block a tick and deeper ones are never taken; shallow water halves the speed; hazards are never entered; the heading
 * turns at most the turn rate; the arrival latch brings a pushed unit back; an impulse stays on walkable surfaces; a
 * vanished surface drops a unit to the next; a blocked unit's stuck count grows; speed comes through the stat service, so
 * a modifier changes the walk.
 */
class MotionSystemTest {
    private static final FactionSlot F0 = new FactionSlot(0);
    private static final double SPEED = 0.25;
    /** The debug walker's radius. */
    private static final double RADIUS = 0.35;

    @Test
    void upStepOverTwoTicks() {
        SimHarness h = harness(VoxelFixtures.heightmap(16, """
                1111222222
                1111222222
                1111222222
                """));
        int unit = h.spawn(F0, new Vec2d(2.5, 1.5), 1, 0)[0];
        order(h, unit, 8.5, 1.5);

        double before = unit(h, unit).y();
        h.runUntil(() -> unit(h, unit).x() + RADIUS >= 4, 40); // its footprint's edge reaches the step (S10)
        double halfway = unit(h, unit).y();
        double centre = unit(h, unit).x();
        h.step();
        double after = unit(h, unit).y();

        assertThat(before).as("on the low floor").isEqualTo(1.0);
        assertThat(centre).as("its centre still on the low floor").isLessThan(4);
        assertThat(halfway)
                .as("halfway up on the tick its footprint reaches the step")
                .isEqualTo(1.5);
        assertThat(after).as("on the new floor the tick after").isEqualTo(2.0);
        assertThat(h.runUntil(() -> unit(h, unit).state() == UnitState.IDLE, 100))
                .isTrue();
        assertThat(unit(h, unit).y()).isEqualTo(2.0);
        assertThat(unit(h, unit).x()).isEqualTo(8.5);
    }

    /** S10: down a one-block step the body stays on the ledge until its whole footprint has left it, as vanilla's. */
    @Test
    void aBodyLeavesAStepOnlyWhenItsFootprintHas() {
        SimHarness h = harness(VoxelFixtures.heightmap(16, """
                2222111111
                2222111111
                """));
        int unit = h.spawn(F0, new Vec2d(1.5, 0.5), 1, 0)[0];
        order(h, unit, 8.5, 0.5);

        h.runUntil(() -> unit(h, unit).x() >= 4, 40);
        assertThat(unit(h, unit).y())
                .as("its centre over the low floor, its footprint still on the step")
                .isEqualTo(2.0);
        h.runUntil(() -> unit(h, unit).x() - RADIUS >= 4, 40);
        h.step();
        h.step();
        assertThat(unit(h, unit).y()).as("down once the footprint has left").isEqualTo(1.0);
    }

    /** A wall's top beside a unit is more than a step up: the body never stands on it. */
    @Test
    void aBodyNeverStandsOnAWallItBrushes() {
        SimHarness h = harness(VoxelFixtures.heightmap(16, """
                1111111111
                1111111111
                4444444444
                """));
        int unit = h.spawn(F0, new Vec2d(1.5, 1.7), 1, 0)[0]; // 0.3 from the wall's column: its footprint overlaps it
        order(h, unit, 8.5, 1.7);
        assertThat(h.runUntil(() -> unit(h, unit).state() == UnitState.IDLE, 100))
                .isTrue();
        assertThat(unit(h, unit).y()).isEqualTo(1.0);
    }

    @Test
    void dropsFallHalfABlockATick() {
        SimHarness h = harness(VoxelFixtures.heightmap(16, """
                4444111111
                4444111111
                """));
        int unit = h.spawn(F0, new Vec2d(1.5, 0.5), 1, 0)[0];
        order(h, unit, 8.5, 0.5);

        h.runUntil(() -> unit(h, unit).x() >= 4, 40);
        double[] ys = new double[7];
        for (int k = 0; k < ys.length; k++) {
            ys[k] = unit(h, unit).y();
            h.step();
        }

        assertThat(ys).as("from 4 down to 1, half a block a tick").containsExactly(3.5, 3.0, 2.5, 2.0, 1.5, 1.0, 1.0);
        assertThat(h.runUntil(() -> unit(h, unit).state() == UnitState.IDLE, 100))
                .isTrue();
        assertThat(unit(h, unit).y()).isEqualTo(1.0);
    }

    @Test
    void aDropDeeperThanThreeIsNeverTaken() {
        SimHarness h = harness(VoxelFixtures.heightmap(16, """
                8888111111
                8888111111
                """));
        int unit = h.spawn(F0, new Vec2d(1.5, 0.5), 1, 0)[0];

        order(h, unit, 8.5, 0.5);
        h.runTicks(60);

        assertThat(unit(h, unit).x()).as("the goal moved to the cliff's edge").isEqualTo(3.5);
        assertThat(unit(h, unit).y()).isEqualTo(8.0);
        assertThat(unit(h, unit).state()).isEqualTo(UnitState.IDLE);
    }

    @Test
    void shallowWaterHalvesTheSpeed() {
        SimHarness h = harness(VoxelFixtures.heightmap(16, """
                ..~~~~~~~~~~~...
                ..~~~~~~~~~~~...
                """));
        int unit = h.spawn(F0, new Vec2d(0.5, 0.5), 1, 0)[0];
        order(h, unit, 15.5, 0.5);
        h.runUntil(() -> unit(h, unit).x() >= 5, 60);

        double x = unit(h, unit).x();
        h.step();

        assertThat(unit(h, unit).x() - x).isCloseTo(SPEED * MotionSystem.SHALLOW, within(1e-12));
    }

    @Test
    void hazardsAreNeverEntered() {
        VoxelWorld w = VoxelFixtures.flat(16, 16, 16, 1);
        w.fill(6, 1, 0, 6, 1, 15, VoxelFixtures.LAVA); // lava across the whole way
        SimHarness h = harness(w);
        int unit = h.spawn(F0, new Vec2d(2.5, 8.5), 1, 0)[0];

        order(h, unit, 12.5, 8.5);
        h.runTicks(80);

        assertThat(unit(h, unit).x()).as("stopped short of the lava").isLessThan(6);
    }

    @Test
    void theHeadingTurnsAtMostTheTurnRate() {
        SimHarness h = harness(VoxelFixtures.flat(32, 32, 16, 1));
        int unit = h.spawn(F0, new Vec2d(16.5, 16.5), 1, 0)[0];
        order(h, unit, 26.5, 16.5); // east
        h.runTicks(4);
        float east = unit(h, unit).yaw();

        order(h, unit, 6.5, 16.5); // about turn, west
        float first = unit(h, unit).yaw();

        float rate = (float) h.queries().templates().byIndex(0).turnRate();
        assertThat(Math.abs(wrap(first - east))).isCloseTo(rate, within(1e-4f));
        h.runTicks(10);
        assertThat(Math.abs(wrap(unit(h, unit).yaw() - east)))
                .as("facing west at last")
                .isCloseTo(180f, within(1e-4f));
    }

    @Test
    void theArrivalLatchBringsAPushedUnitBack() {
        SimHarness h = harness(VoxelFixtures.flat(32, 32, 16, 1));
        int unit = h.spawn(F0, new Vec2d(4.5, 4.5), 1, 0)[0];
        order(h, unit, 10.5, 4.5);
        assertThat(h.runUntil(() -> unit(h, unit).state() == UnitState.IDLE, 100))
                .isTrue();

        MotionSystem.impulse(state(h), unit, 0, 0.75); // within the latch: it stays
        h.runTicks(5);
        assertThat(unit(h, unit).state()).isEqualTo(UnitState.IDLE);
        assertThat(unit(h, unit).z()).isEqualTo(5.25);

        MotionSystem.impulse(state(h), unit, 0, 1.5); // pushed off: it walks back
        h.step();
        assertThat(unit(h, unit).state()).isEqualTo(UnitState.WALKING);
        assertThat(h.runUntil(() -> unit(h, unit).state() == UnitState.IDLE, 100))
                .isTrue();
        assertThat(unit(h, unit).x()).isEqualTo(10.5);
        assertThat(unit(h, unit).z()).isEqualTo(4.5);
    }

    @Test
    void anImpulseStaysOnWalkableSurfaces() {
        VoxelWorld w = VoxelFixtures.flat(16, 16, 16, 1);
        w.fill(8, 1, 0, 8, 4, 15, VoxelFixtures.SOLID); // a wall at x 8
        SimHarness h = harness(w);
        int unit = h.spawn(F0, new Vec2d(5.5, 5.5), 1, 0)[0];
        h.step();

        MotionSystem.impulse(state(h), unit, 5, 0);

        UnitView after = unit(h, unit);
        assertThat(after.x()).as("stopped at the wall").isLessThan(8).isGreaterThanOrEqualTo(7);
        assertThat(after.z()).isEqualTo(5.5);
    }

    @Test
    void aVanishedSurfaceDropsTheUnitToTheNextBelow() {
        VoxelWorld w = VoxelFixtures.flat(16, 16, 16, 1);
        w.fill(4, 1, 4, 6, 2, 6, VoxelFixtures.SOLID); // a platform two blocks up
        SimHarness h = harness(w);
        int unit = h.spawn(F0, new Vec2d(5.5, 5.5), 1, 0)[0];
        h.step();
        h.step();
        assertThat(unit(h, unit).y())
                .as("snapped onto the platform at first contact")
                .isEqualTo(3.0);

        w.fill(4, 1, 4, 6, 2, 6, VoxelFixtures.AIR);
        PageCoord page = new PageCoord(0, 0);
        h.publish(TerrainPage.build(page, w.slice(page), new PageBuilder(MobilityClass.INFANTRY)));
        double[] ys = new double[5];
        for (int k = 0; k < ys.length; k++) {
            h.step();
            ys[k] = unit(h, unit).y();
        }

        assertThat(ys).containsExactly(2.5, 2.0, 1.5, 1.0, 1.0);
    }

    @Test
    void aSpeedModifierChangesTheWalk() {
        SimHarness h = harness(VoxelFixtures.flat(32, 32, 16, 1));
        int unit = h.spawn(F0, new Vec2d(4.5, 16.5), 1, 0)[0];
        state(h).stats().add(F0, Modifier.mul(CorePaths.WALK_SPEED, 2, 1));
        order(h, unit, 28.5, 16.5);

        double x = unit(h, unit).x();
        h.step();

        assertThat(unit(h, unit).x() - x).isCloseTo(2 * SPEED, within(1e-12));
    }

    @Test
    void aBlockedUnitsStuckCountGrows() {
        VoxelWorld w = VoxelFixtures.flat(16, 16, 16, 1);
        SimHarness h = harness(w);
        int unit = h.spawn(F0, new Vec2d(4.5, 5.5), 1, 0)[0];
        order(h, unit, 14.5, 5.5);
        long ordered = h.tick();

        w.fill(6, 1, 0, 6, 4, 15, VoxelFixtures.SOLID); // a wall across the whole way, the next tick
        PageCoord page = new PageCoord(0, 0);
        h.publish(TerrainPage.build(page, w.slice(page), new PageBuilder(MobilityClass.INFANTRY)));
        h.runUntil(() -> h.tick() >= ordered + 19, 40);

        assertThat(unit(h, unit).x())
                .as("pressed against the wall")
                .isGreaterThan(5.5)
                .isLessThan(6);
        assertThat(state(h).motion().stuck(Handle.index(unit)))
                .as("ticks without progress before the repath")
                .isGreaterThanOrEqualTo(10);
    }

    @Test
    void restingGroundFollowsTerrainAndKeepsTwoTickHeightEasing() {
        VoxelWorld world = VoxelFixtures.flat(16, 16, 16, 1);
        SimHarness h = harness(world);
        int body = h.spawn(F0, new Vec2d(1.7, 8.5), 1, 0)[0];
        h.runTicks(8);
        world.fill(2, 1, 8, 2, 1, 8, VoxelFixtures.SOLID);
        h.publishAll(world);
        h.step();
        assertThat(unit(h, body).y()).isEqualTo(1.5);
        h.step();
        assertThat(unit(h, body).y()).isEqualTo(2.0);
        world.fill(2, 1, 8, 2, 1, 8, VoxelFixtures.AIR);
        h.publishAll(world);
        h.step();
        assertThat(unit(h, body).y()).isEqualTo(1.5);
        h.step();
        assertThat(unit(h, body).y()).isEqualTo(1.0);
        order(h, body, 8.5, 8.5);
        assertThat(h.runUntil(() -> unit(h, body).state() == UnitState.IDLE, 100))
                .isTrue();
        assertThat(unit(h, body).x()).isEqualTo(8.5);
    }

    @Test
    void idleGeometryFollowsFootprintEdgesAndExternalHeightWithinOneColumn() {
        VoxelWorld world = VoxelFixtures.flat(16, 16, 16, 1);
        world.fill(2, 1, 8, 2, 1, 8, VoxelFixtures.SOLID);
        SimHarness h = harness(world);
        int body = h.spawn(F0, new Vec2d(1.5, 8.5), 1, 0)[0];
        h.runTicks(8);
        var motion = state(h).motion();
        int index = Handle.index(body);
        motion.set(index, 1.7, 1, 8.5, motion.yaw(index));
        h.step();
        assertThat(unit(h, body).y()).isEqualTo(1.5);
        h.step();
        assertThat(unit(h, body).y()).isEqualTo(2.0);
        motion.set(index, 1.5, 2, 8.5, motion.yaw(index));
        h.step();
        assertThat(unit(h, body).y()).isEqualTo(1.5);
        h.step();
        assertThat(unit(h, body).y()).isEqualTo(1.0);
        motion.set(index, 1.5, 3, 8.5, motion.yaw(index));
        h.step();
        assertThat(unit(h, body).y()).isEqualTo(2.5);
        h.runTicks(3);
        assertThat(unit(h, body).y()).isEqualTo(1.0);
    }

    private static float wrap(float degrees) {
        float d = degrees;
        while (d > 180f) {
            d -= 360f;
        }
        while (d <= -180f) {
            d += 360f;
        }
        return d;
    }

    /** A harness the size of {@code world} whose pages are published on the first step. */
    private static SimHarness harness(VoxelWorld world) {
        SimHarness h = SimHarness.builder().size(world.sizeX(), world.sizeZ()).build();
        h.publishAll(world);
        return h;
    }

    private static void order(SimHarness h, int unit, double x, double z) {
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(unit), new Vec2d(x, z)));
        h.step();
    }

    private static UnitView unit(SimHarness h, int unit) {
        UnitView view = h.queries().unit(unit);
        assertThat(view).isNotNull();
        return view;
    }

    private static MatchState state(SimHarness h) {
        return KernelAccess.state(h.core());
    }
}
