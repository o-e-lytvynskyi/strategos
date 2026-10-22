package dev.strategos.core.sim.move;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.Agent;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.testkit.Scenario;
import dev.strategos.core.testkit.ScenarioReport;
import dev.strategos.core.testkit.Scenarios;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PATH-06 in L1 (spec §5.11): units walking at each other pass and arrive, never overlapping; a unit walks around an
 * agent standing in its way; an idle unit steps aside for a walker; two units on one point part; a crowd spreads by
 * separation; a body keeps off a wall while a unit's own route may run along it; and the same inputs give the same
 * state.
 */
class AvoidanceTest {
    private static final FactionSlot F0 = new FactionSlot(0);
    private static final FactionSlot F1 = new FactionSlot(1);

    @Test
    void headOnGroupsPassThroughAGap() {
        Scenario scenario = Scenarios.headOnGap();
        SimHarness h = SimHarness.forScenario(scenario);
        ScenarioReport report = h.play(scenario);

        assertThat(report.passed()).as(String.valueOf(report.failure())).isTrue();
    }

    @Test
    void headOnUnitsPassAndArrive() {
        SimHarness h = harness(VoxelFixtures.flat(32, 16, 8, 1));
        int a = h.spawn(F0, new Vec2d(4.5, 8.5), 1, 0)[0];
        int b = h.spawn(F1, new Vec2d(27.5, 8.5), 1, 0)[0];
        order(h, a, 27.5, 8.5);
        order(h, b, 4.5, 8.5);
        double closest = Double.POSITIVE_INFINITY;
        for (int t = 0; t < 200 && !(idle(h, a) && idle(h, b)); t++) {
            h.step();
            closest = Math.min(closest, distance(unit(h, a), unit(h, b)));
        }

        assertThat(closest).as("never overlapping").isGreaterThan(0.69);
        assertThat(unit(h, a).x()).isEqualTo(27.5);
        assertThat(unit(h, b).x()).isEqualTo(4.5);
    }

    @Test
    void anAgentInTheWayIsWalkedAround() {
        SimHarness h = harness(VoxelFixtures.flat(32, 16, 8, 1));
        int unit = h.spawn(F0, new Vec2d(4.5, 8.5), 1, 0)[0];
        h.agents(tick -> List.of(new Agent(7, 16.5f, 1, 8.5f, 0, 0, 0.3f)));
        order(h, unit, 27.5, 8.5);
        double closest = Double.POSITIVE_INFINITY;
        for (int t = 0; t < 200 && !idle(h, unit); t++) {
            h.step();
            UnitView u = unit(h, unit);
            closest = Math.min(closest, Math.hypot(u.x() - 16.5, u.z() - 8.5));
        }

        assertThat(closest).as("clear of the agent's body").isGreaterThan(0.35 + 0.3 - 0.01);
        assertThat(unit(h, unit).x()).isEqualTo(27.5);
    }

    @Test
    void anIdleUnitStepsAsideForAWalker() {
        SimHarness h = harness(VoxelFixtures.flat(32, 16, 8, 1));
        int walker = h.spawn(F0, new Vec2d(4.5, 8.5), 1, 0)[0];
        int idle = h.spawn(F1, new Vec2d(16.5, 8.5), 1, 0)[0];
        order(h, walker, 27.5, 8.5);
        double closest = Double.POSITIVE_INFINITY;
        for (int t = 0; t < 200 && !idle(h, walker); t++) {
            h.step();
            closest = Math.min(closest, distance(unit(h, walker), unit(h, idle)));
        }

        assertThat(closest).as("no overlap").isGreaterThan(0.6);
        assertThat(unit(h, idle).z()).as("the idle unit stepped aside").isNotEqualTo(8.5);
        assertThat(unit(h, walker).x()).isEqualTo(27.5);
    }

    @Test
    void unitsOnOnePointPart() {
        SimHarness h = harness(VoxelFixtures.flat(16, 16, 8, 1));
        int a = h.spawn(F0, new Vec2d(8.5, 8.5), 1, 0)[0];
        int b = h.spawn(F0, new Vec2d(8.5, 8.5), 1, 0)[0];
        h.runTicks(40);

        assertThat(distance(unit(h, a), unit(h, b))).as("apart").isGreaterThan(0.5);
        assertThat(unit(h, a).x())
                .as("the lower index went west")
                .isLessThan(unit(h, b).x());
    }

    @Test
    void aCrowdSpreadsBySeparation() {
        SimHarness h = harness(VoxelFixtures.flat(16, 16, 8, 1));
        int[] units = h.spawn(F0, new Vec2d(8.5, 8.5), 12, 0.6);
        double before = spread(h, units);
        h.runTicks(80);

        assertThat(spread(h, units)).as("spread wider").isGreaterThan(before + 0.5);
        for (int u : units) {
            assertThat(Double.isFinite(unit(h, u).x())).isTrue();
        }
    }

    @Test
    void theSameInputsGiveTheSameState() {
        assertThat(crowdCrossing()).isEqualTo(crowdCrossing());
    }

    /** Two groups of eight walk through each other; the state hash at the end. */
    private static long crowdCrossing() {
        SimHarness h = harness(VoxelFixtures.flat(48, 24, 8, 1));
        int[] west = h.spawn(F0, new Vec2d(6, 12), 8, 2);
        int[] east = h.spawn(F1, new Vec2d(42, 12), 8, 2);
        for (int k = 0; k < 8; k++) {
            h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(west[k]), new Vec2d(40.5, 8.5 + k)));
            h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(east[k]), new Vec2d(7.5, 8.5 + k)));
        }
        h.agents(tick -> tick % 3 == 0 ? List.of(new Agent(1, 24.5f, 1, 12.5f, 0.1f, 0, 0.3f)) : List.of());
        h.runTicks(300);
        return h.stateHash();
    }

    @Test
    void aBodyKeepsOffAWallWhileItsRouteRunsAlongIt() {
        VoxelWorld w = VoxelFixtures.flat(32, 16, 8, 1);
        w.fill(0, 1, 9, 31, 3, 9, VoxelFixtures.SOLID); // a wall north of the lane z = 8
        SimHarness h = harness(w);
        int walker = h.spawn(F0, new Vec2d(4.5, 8.5), 1, 0)[0];
        int idle = h.spawn(F1, new Vec2d(16.5, 8.5), 1, 0)[0];
        order(h, walker, 27.5, 8.5);
        double north = 0;
        for (int t = 0; t < 200 && !idle(h, walker); t++) {
            h.step();
            north = Math.max(north, unit(h, idle).z());
        }

        assertThat(north)
                .as("the idle unit never stepped into the wall's reach")
                .isLessThanOrEqualTo(9 - 0.35 + 1e-9);
        assertThat(unit(h, walker).x()).as("the walker arrived").isEqualTo(27.5);
    }

    private static double spread(SimHarness h, int[] units) {
        double sum = 0;
        for (int a : units) {
            for (int b : units) {
                sum += distance(unit(h, a), unit(h, b));
            }
        }
        return sum / (units.length * units.length);
    }

    private static double distance(UnitView a, UnitView b) {
        return Math.hypot(a.x() - b.x(), a.z() - b.z());
    }

    private static boolean idle(SimHarness h, int unit) {
        return unit(h, unit).state() == UnitState.IDLE;
    }

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
}
