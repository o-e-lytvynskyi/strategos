package dev.strategos.core.sim.move;

import dev.strategos.core.shared.HotPath;
import dev.strategos.core.sim.Agent;
import dev.strategos.core.sim.AgentUpdate;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.nav.Dir;
import dev.strategos.core.sim.nav.SurfaceGrid;
import dev.strategos.core.sim.spatial.UnitFilter;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Plans a unit's velocity for the tick around the others (PATH-06, spec §5.11), before any unit moves, so every plan
 * sees the positions and velocities the units ended the last tick with, through the grid the last {@code MOVEMENT}
 * rebuilt.
 *
 * <ul>
 *   <li>Neighbours: the units within {@value #NEIGHBOUR_RANGE} blocks on the same surface layer (heights less than
 *       {@value #LAYER} blocks apart), at most {@value #MAX_NEIGHBOURS} by (distance, index), and the tick's agents
 *       within that range.
 *   <li>A unit with more than {@value #CROWD} of them within {@value Separation#RANGE} blocks steers by
 *       {@link Separation} instead.
 *   <li>Otherwise ORCA keeps it clear of them for {@value #HORIZON} ticks, taking half of the avoidance of another
 *       unit, which takes the other half, and the whole of an agent's.
 *   <li>Blocked surface edges within {@value #EDGE_RANGE} blocks are static limits: the speed toward such an edge is
 *       at most (distance - radius) / {@value #EDGE_TICKS} blocks a tick, or the unit's own preferred speed toward it,
 *       whichever is more, so the edges stop avoidance from pushing a body into a wall but never the unit's own
 *       steering.
 * </ul>
 *
 * With nothing near, the plan is the preferred velocity itself, bit for bit.
 */
@HotPath
final class Avoidance {
    static final double NEIGHBOUR_RANGE = 3;
    static final int MAX_NEIGHBOURS = 10;
    static final double HORIZON = 20;
    static final int CROWD = 6;
    static final double EDGE_RANGE = 1.5;
    static final double EDGE_TICKS = 10;
    static final double LAYER = 2;

    /** Lets a step of exactly the unit's speed through untouched: rounding may make it a few ulps longer. */
    static final double SPEED_TOLERANCE = 1e-6;

    /** How far apart two units on one point are taken to be, along X, so their lines have a direction. */
    private static final double APART = 1e-6;

    /** Two blocked edges at most along each of the four axis directions. */
    private final Orca orca = new Orca(8 + MAX_NEIGHBOURS + AgentUpdate.MAX_AGENTS);

    private final Separation separation = new Separation();
    private final int[] near = new int[MAX_NEIGHBOURS];
    private final double[] nearDistSq = new double[MAX_NEIGHBOURS];
    private final IntConsumer collector = this::collect;
    private final MotionStore motion;
    private int nearCount;
    private int crowd;
    private int self;
    private double selfX;
    private double selfY;
    private double selfZ;
    private double planX;
    private double planZ;

    Avoidance(MotionStore motion) {
        this.motion = motion;
    }

    /**
     * Plans unit {@code i}'s velocity, preferring (prefX, prefZ) within {@code maxSpeed}; read it with {@link #x()},
     * {@link #z()}.
     */
    void plan(MatchState s, int i, double prefX, double prefZ, double maxSpeed) {
        self = i;
        selfX = motion.x(i);
        selfY = motion.y(i);
        selfZ = motion.z(i);
        nearCount = 0;
        crowd = 0;
        s.spatial().forEachInRadius(selfX, selfZ, NEIGHBOUR_RANGE, UnitFilter.ANY, collector);
        List<Agent> agents = s.agents();
        double range = Separation.RANGE * Separation.RANGE;
        for (int k = 0; k < agents.size(); k++) {
            Agent agent = agents.get(k);
            if (sameLayer(agent.y()) && distSq(agent.x(), agent.z()) < range) {
                crowd++;
            }
        }
        if (crowd > CROWD) {
            separate(agents, prefX, prefZ, maxSpeed);
            return;
        }
        double radius = radius(s, i);
        orca.clear();
        limits(s, i, radius, prefX, prefZ);
        double vx = motion.vx(i);
        double vz = motion.vz(i);
        for (int k = 0; k < nearCount; k++) {
            int j = near[k];
            double relX = motion.x(j) - selfX;
            double relZ = motion.z(j) - selfZ;
            if (relX == 0 && relZ == 0) {
                relX = j > i ? APART : -APART; // the higher index stands east
            }
            orca.addAgent(relX, relZ, vx, vz, motion.vx(j), motion.vz(j), radius + radius(s, j), HORIZON, 0.5);
        }
        double agentRange = NEIGHBOUR_RANGE * NEIGHBOUR_RANGE;
        for (int k = 0; k < agents.size(); k++) {
            Agent agent = agents.get(k);
            if (!sameLayer(agent.y()) || distSq(agent.x(), agent.z()) > agentRange) {
                continue;
            }
            double relX = agent.x() - selfX;
            double relZ = agent.z() - selfZ;
            if (relX == 0 && relZ == 0) {
                relX = APART;
            }
            orca.addAgent(relX, relZ, vx, vz, agent.vx(), agent.vz(), radius + agent.radius(), HORIZON, 1);
        }
        if (orca.lines() == 0) {
            planX = prefX;
            planZ = prefZ;
            return;
        }
        orca.solve(prefX, prefZ, maxSpeed + SPEED_TOLERANCE);
        planX = orca.x();
        planZ = orca.z();
    }

    double x() {
        return planX;
    }

    double z() {
        return planZ;
    }

    private void separate(List<Agent> agents, double prefX, double prefZ, double maxSpeed) {
        separation.start(prefX, prefZ, maxSpeed);
        for (int k = 0; k < nearCount; k++) {
            int j = near[k];
            separation.push(motion.x(j) - selfX, motion.z(j) - selfZ, j < self);
        }
        for (int k = 0; k < agents.size(); k++) {
            Agent agent = agents.get(k);
            if (sameLayer(agent.y())) {
                separation.push(agent.x() - selfX, agent.z() - selfZ, false);
            }
        }
        planX = separation.x();
        planZ = separation.z();
    }

    /** The grid's consumer: unit {@code j} within range, kept among the nearest by (distance, index). */
    private void collect(int j) {
        if (j == self || !sameLayer(motion.y(j))) {
            return;
        }
        double d2 = distSq(motion.x(j), motion.z(j));
        if (d2 < Separation.RANGE * Separation.RANGE) {
            crowd++;
        }
        if (nearCount == MAX_NEIGHBOURS && d2 >= nearDistSq[MAX_NEIGHBOURS - 1]) {
            return; // the grid reports by index, so a tie keeps the lower one
        }
        int k = nearCount < MAX_NEIGHBOURS ? nearCount++ : MAX_NEIGHBOURS - 1;
        while (k > 0 && d2 < nearDistSq[k - 1]) {
            near[k] = near[k - 1];
            nearDistSq[k] = nearDistSq[k - 1];
            k--;
        }
        near[k] = j;
        nearDistSq[k] = d2;
    }

    /**
     * The static limits of the blocked edges within {@value #EDGE_RANGE} blocks, looking along each axis from the
     * unit's surface through the surfaces it links to.
     */
    private void limits(MatchState s, int i, double radius, double prefX, double prefZ) {
        int floor = motion.floor(i);
        if (floor == MotionStore.NO_FLOOR) {
            return;
        }
        SurfaceGrid grid = s.surfaces();
        int cx = (int) Math.floor(selfX);
        int cz = (int) Math.floor(selfZ);
        int layer = MotionSystem.layerOf(grid, cx, cz, floor);
        if (layer < 0) {
            return;
        }
        for (int dir = Dir.N; dir < Dir.COUNT; dir += 2) { // the orthogonal directions
            int dx = Dir.dx(dir);
            int dz = Dir.dz(dir);
            double distance = dx > 0 ? cx + 1 - selfX : dx < 0 ? selfX - cx : dz > 0 ? cz + 1 - selfZ : selfZ - cz;
            int x = cx;
            int z = cz;
            int l = layer;
            while (distance < EDGE_RANGE) {
                int next = grid.neighbour(x, z, l, dir);
                if (next < 0) {
                    double limit = Math.max((distance - radius) / EDGE_TICKS, prefX * dx + prefZ * dz);
                    orca.addLimit(dx, dz, limit);
                    break;
                }
                x += dx;
                z += dz;
                l = next;
                distance += 1;
            }
        }
    }

    private boolean sameLayer(double y) {
        return Math.abs(y - selfY) < LAYER;
    }

    private double distSq(double x, double z) {
        double dx = x - selfX;
        double dz = z - selfZ;
        return dx * dx + dz * dz;
    }

    private static double radius(MatchState s, int i) {
        return s.templates().byIndex(s.identity().templateIndex(i)).radius();
    }
}
