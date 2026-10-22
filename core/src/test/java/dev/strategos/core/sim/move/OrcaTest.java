package dev.strategos.core.sim.move;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * PATH-06.4 at the solver (spec §5.11): equal inputs give equal velocities; two agents head-on pass each other, each
 * taking half; a non-reactive agent is walked around by the one that takes the whole; with no binding line the
 * preferred velocity comes back bit for bit; a static limit holds where the agent lines allow nothing.
 */
class OrcaTest {
    private static final double RADIUS = 0.35;
    private static final double SPEED = 0.25;
    private static final double HORIZON = 20;

    @Test
    void equalInputsGiveEqualVelocities() {
        SplittableRandom random = new SplittableRandom(11);
        Orca a = new Orca(4);
        Orca b = new Orca(32);
        for (int round = 0; round < 1_000; round++) {
            a.clear();
            b.clear();
            if (random.nextBoolean()) {
                double limit = random.nextDouble(-0.05, 0.1);
                a.addLimit(1, 0, limit);
                b.addLimit(1, 0, limit);
            }
            int lines = random.nextInt(1, 14);
            for (int k = 0; k < lines; k++) {
                double[] v = new double[8];
                for (int c = 0; c < 6; c++) {
                    v[c] = random.nextDouble(-2, 2) * (c < 2 ? 1 : 0.15);
                }
                a.addAgent(v[0], v[1], v[2], v[3], v[4], v[5], 2 * RADIUS, HORIZON, 0.5);
                b.addAgent(v[0], v[1], v[2], v[3], v[4], v[5], 2 * RADIUS, HORIZON, 0.5);
            }
            double prefX = random.nextDouble(-SPEED, SPEED);
            double prefZ = random.nextDouble(-SPEED, SPEED);
            a.solve(prefX, prefZ, SPEED);
            b.solve(prefX, prefZ, SPEED);

            assertThat(Double.doubleToLongBits(a.x())).isEqualTo(Double.doubleToLongBits(b.x()));
            assertThat(Double.doubleToLongBits(a.z())).isEqualTo(Double.doubleToLongBits(b.z()));
            assertThat(Math.hypot(a.x(), a.z())).as("within the speed").isLessThanOrEqualTo(SPEED + 1e-9);
        }
    }

    @Test
    void headOnPairPasses() {
        double[] x = {0, 10};
        double[] z = {0, 0};
        double[] goalX = {10, 0};
        double[] vx = new double[2];
        double[] vz = new double[2];
        Orca orca = new Orca(4);
        double closest = Double.POSITIVE_INFINITY;
        for (int tick = 0; tick < 120; tick++) {
            double[] nextVx = new double[2];
            double[] nextVz = new double[2];
            for (int i = 0; i < 2; i++) {
                int j = 1 - i;
                orca.clear();
                orca.addAgent(x[j] - x[i], z[j] - z[i], vx[i], vz[i], vx[j], vz[j], 2 * RADIUS, HORIZON, 0.5);
                double[] pref = toward(goalX[i] - x[i], -z[i]);
                orca.solve(pref[0], pref[1], SPEED);
                nextVx[i] = orca.x();
                nextVz[i] = orca.z();
            }
            for (int i = 0; i < 2; i++) {
                vx[i] = nextVx[i];
                vz[i] = nextVz[i];
                x[i] += vx[i];
                z[i] += vz[i];
            }
            closest = Math.min(closest, Math.hypot(x[1] - x[0], z[1] - z[0]));
        }

        assertThat(closest).as("never closer than their radii").isGreaterThanOrEqualTo(2 * RADIUS - 0.01);
        assertThat(Math.hypot(x[0] - 10, z[0])).as("the first reached the east").isLessThan(0.05);
        assertThat(Math.hypot(x[1], z[1])).as("the second reached the west").isLessThan(0.05);
    }

    @Test
    void nonReactiveAgentIsWalkedAround() {
        double x = 0;
        double z = 0;
        double vx = 0;
        double vz = 0;
        double agentX = 5;
        double agentZ = 0.05;
        Orca orca = new Orca(4);
        double closest = Double.POSITIVE_INFINITY;
        for (int tick = 0; tick < 120; tick++) {
            orca.clear();
            orca.addAgent(agentX - x, agentZ - z, vx, vz, 0, 0, RADIUS + 0.3, HORIZON, 1);
            double[] pref = toward(10 - x, -z);
            orca.solve(pref[0], pref[1], SPEED);
            vx = orca.x();
            vz = orca.z();
            x += vx;
            z += vz;
            closest = Math.min(closest, Math.hypot(agentX - x, agentZ - z));
        }

        assertThat(closest).as("clear of the agent").isGreaterThanOrEqualTo(RADIUS + 0.3 - 0.01);
        assertThat(Math.hypot(x - 10, z)).as("past it, at its goal").isLessThan(0.05);
    }

    @Test
    void anUnboundPreferenceComesBackExactly() {
        Orca orca = new Orca(4);
        double prefX = 0.1 / 3;
        double prefZ = Math.sqrt(SPEED * SPEED - prefX * prefX);
        orca.addLimit(-1, 0, 0.01); // a wall to the west, the unit walks east
        orca.addAgent(-2, -2, prefX, prefZ, 0, 0, 2 * RADIUS, HORIZON, 0.5); // behind it, standing
        orca.solve(prefX, prefZ, SPEED + 1e-6);

        assertThat(orca.x()).isEqualTo(prefX);
        assertThat(orca.z()).isEqualTo(prefZ);
    }

    @Test
    void aLimitHoldsWhenTheAgentLinesAllowNothing() {
        Orca orca = new Orca(2);
        orca.addLimit(1, 0, 0.02); // at most 0.02 a tick east
        for (int k = 0; k < 6; k++) { // ringed in at contact on every side
            double angle = Math.PI * 2 * k / 6;
            orca.addAgent(Math.cos(angle) * RADIUS, Math.sin(angle) * RADIUS, 0, 0, 0, 0, 2 * RADIUS, HORIZON, 0.5);
        }
        orca.solve(SPEED, 0, SPEED);

        assertThat(orca.lines()).isEqualTo(7);
        assertThat(orca.x()).as("the limit is never given up").isLessThanOrEqualTo(0.02 + 1e-12);
        assertThat(Double.isFinite(orca.z())).isTrue();
    }

    /** A step toward a goal (dx, dz) away: the speed along it, or the goal itself within one step. */
    private static double[] toward(double dx, double dz) {
        double d = Math.hypot(dx, dz);
        return d <= SPEED ? new double[] {dx, dz} : new double[] {dx / d * SPEED, dz / d * SPEED};
    }
}
