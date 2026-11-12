package dev.strategos.core.sim.move;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.Agent;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.Stagger;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.testkit.Scenario;
import dev.strategos.core.testkit.ScenarioReport;
import dev.strategos.core.testkit.Scenarios;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import java.util.List;
import java.util.Random;
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
    void restingNeighboursStayStillAndStillReactToAnAgent() {
        SimHarness h = harness(VoxelFixtures.flat(32, 16, 8, 1));
        int a = h.spawn(F0, new Vec2d(12.5, 8.5), 1, 0)[0];
        int b = h.spawn(F0, new Vec2d(13.5, 8.5), 1, 0)[0];
        h.runTicks(100);
        assertThat(unit(h, a).x()).isEqualTo(12.5);
        assertThat(unit(h, b).x()).isEqualTo(13.5);
        h.agents(tick -> List.of(new Agent(7, 12.5f, 1, 8.5f, 0, 0, 0.3f)));
        h.runTicks(12);
        assertThat(Math.hypot(unit(h, a).x() - 12.5, unit(h, a).z() - 8.5))
                .as("an overlapping stationary agent still displaces the resting unit")
                .isGreaterThan(0.1);
    }

    @Test
    void conservativeRestBoundAgreesWithTheFullOrcaSolver() {
        Random random = new Random(7521);
        Orca reference = new Orca(1);
        int accepted = 0;
        for (int sample = 0; sample < 10_000; sample++) {
            double x = random.nextDouble() * 6 - 3;
            double z = random.nextDouble() * 6 - 3;
            double reach = 0.2 + random.nextDouble();
            double vx = random.nextDouble() * 0.16 - 0.08;
            double vz = random.nextDouble() * 0.16 - 0.08;
            if (Avoidance.clearForHorizon(x * x + z * z, reach + 1e-9, vx, vz)) {
                accepted++;
                reference.clear();
                reference.addAgent(x, z, 0, 0, vx, vz, reach, Avoidance.HORIZON, 0.5);
                reference.solve(0, 0, 0.5);
                assertThat(reference.x()).isZero();
                assertThat(reference.z()).isZero();
            }
        }
        assertThat(accepted).isGreaterThan(1_000);
        assertThat(Avoidance.clearForHorizon(1, 0.6, -0.1, 0)).isFalse();
        assertThat(Avoidance.clearForHorizon(0.25, 0.6, 0, 0)).isFalse();
    }

    @Test
    void aRestProbeKeepsTheOriginalGridRadiusAfterAnExternalDisplacement() {
        SimHarness h = harness(VoxelFixtures.flat(32, 16, 8, 1));
        int a = h.spawn(F0, new Vec2d(12.5, 8.5), 1, 0)[0];
        int b = h.spawn(F0, new Vec2d(15.3, 8.5), 1, 0)[0];
        h.runTicks(8);
        while (!Stagger.selects(h.tick() + 1, Handle.index(a), MotionSystem.IDLE_EVERY)) {
            h.step();
        }
        var motion = KernelAccess.state(h.core()).motion();
        int other = Handle.index(b);
        // Between movements the grid deliberately retains its previous positions. The
        // original 3-block query sees this candidate; a smaller rest-only query could miss it.
        motion.set(other, 12.9, motion.y(other), 8.5, motion.yaw(other));
        h.step();
        assertThat(unit(h, a).x())
                .as("the same tick still reacts to the displaced neighbour")
                .isLessThan(12.5);
    }

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

    /**
     * A crowd standing on the 32-block floor, as the L1 floor under the L6 comparison of standing and walking units: its
     * hash after 300 ticks was taken before the crowd was told from the units within the separation range alone, and
     * before the neighbours were read in the grid's own order. A pose set from outside between two movements leaves the
     * grid behind, and the plan then looks at the whole neighbourhood, as it always did.
     */
    @Test
    void aStandingCrowdKeepsItsHistory() {
        assertThat(standingCrowd(false, false)).isEqualTo(-3768017173266802458L);
        assertThat(standingCrowd(true, false))
                .as("one unit displaced from outside")
                .isEqualTo(2536433895209204293L);
        assertThat(standingCrowd(false, true)).as("an agent beside it").isEqualTo(-7976379457236708123L);
    }

    private static long standingCrowd(boolean displace, boolean agent) {
        SimHarness h = harness(VoxelFixtures.flat(32, 32, 8, 1));
        int[] crowd = h.spawn(F0, new Vec2d(16.5, 16.5), 60, 4);
        h.spawn(F1, new Vec2d(8.5, 8.5), 6, 1);
        if (agent) {
            h.agents(tick -> tick % 5 == 0 ? List.of(new Agent(1, 17.5f, 1, 16.5f, 0.1f, 0, 0.3f)) : List.of());
        }
        h.runTicks(40);
        if (displace) {
            var motion = KernelAccess.state(h.core()).motion();
            int i = Handle.index(crowd[7]);
            motion.set(i, motion.x(i) + 1.2, motion.y(i), motion.z(i) - 0.7, motion.yaw(i));
        }
        h.runTicks(260);
        return h.stateHash();
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
