package dev.strategos.core.sim.unitai;

import dev.strategos.core.content.Footprint;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.combat.Siege;
import dev.strategos.core.sim.nav.Reach;
import dev.strategos.core.sim.nav.Surface;
import dev.strategos.core.sim.nav.SurfaceGrid;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.unitai.Hfsm.Context;
import java.util.List;

/**
 * An attack-move that finds a wall in its way (BLD-13, M9 spec §2.10): its goal is unreachable for its faction, so its
 * path ended at the nearest point it could reach and it has no way nearer. Instead of giving the order up it attacks
 * the nearest wall segment of another faction within {@value #REACH} blocks, as an attack on any building is made
 * (torches, rams: CMB-03, CMB-09), and goes on with the order when the segment has fallen: through the gap, or to the
 * next segment, until nothing blocks it or nothing it can strike is near. Only a goal the unit's
 * faction cannot reach ({@link Reach}: its own ground and the gates of its allies) breaches: a unit that ends short of
 * its goal for crowding leaves the wall alone. It strikes the nearest segment on the goal's side of it, and a segment whose
 * attack failed is not struck again by that unit for {@value #COOLDOWN} ticks, so a breach that cannot succeed does not
 * loop. The cooldowns are {@link dev.strategos.core.sim.BreachLog}'s, saved with the match.
 */
final class Breaching {
    /** The class the segments of a wall have. */
    static final String WALL = "Wall";

    /** How far from itself, in blocks to a segment's nearest column, a unit looks for the wall that blocks it. */
    static final double REACH = 6;

    /** A goal this near counts as reached (a crowd's places lie round it). */
    static final double SHORT = 3;

    /** How long a unit leaves a segment it has attacked alone, in ticks. */
    static final int COOLDOWN = 200;

    private Breaching() {}

    /** Whether the unit's attack-move ends more than {@value #SHORT} blocks from its goal: the way is shut. */
    static boolean shortOfGoal(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        return s.orders().kind(i) == OrderKind.ATTACK_MOVE
                && DetMath.length(
                                s.orders().targetX(i) - s.motion().x(i),
                                s.orders().targetZ(i) - s.motion().z(i))
                        > SHORT;
    }

    /**
     * Whether the unit's faction can walk from where it stands to a surface of its attack-move's goal column. False
     * when it stands on none, or the goal column has no surface: the way is shut as far as it can tell.
     */
    private static boolean goalReachable(MatchState s, int i) {
        SurfaceGrid grid = s.surfacesFor(i);
        MotionStore motion = s.motion();
        int cx = (int) Math.floor(motion.x(i));
        int cz = (int) Math.floor(motion.z(i));
        int gx = (int) Math.floor(s.orders().targetX(i));
        int gz = (int) Math.floor(s.orders().targetZ(i));
        if (cx < 0
                || cz < 0
                || cx >= grid.sizeX()
                || cz >= grid.sizeZ()
                || gx < 0
                || gz < 0
                || gx >= grid.sizeX()
                || gz >= grid.sizeZ()
                || motion.floor(i) == MotionStore.NO_FLOOR) {
            return false;
        }
        int from = -1;
        for (int layer = 0; layer < grid.count(cx, cz); layer++) {
            if (Surface.floor(grid.surface(cx, cz, layer)) == motion.floor(i)) {
                from = grid.node(cx, cz, layer);
            }
        }
        if (from < 0) {
            return false;
        }
        java.util.BitSet reach =
                Reach.reachableFrom(grid, s.gates(), s.allies(), s.identity().faction(i), grid.component(from));
        for (int layer = 0; layer < grid.count(gx, gz); layer++) {
            if (Surface.passable(grid.surface(gx, gz, layer)) && reach.get(grid.component(grid.node(gx, gz, layer)))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Puts the attack on the nearest wall segment before the unit's attack-move, or sends a unit whose breach has opened
     * the way on a new path; false when the order is over (nothing blocks it, or nothing it can strike is near).
     */
    static boolean breach(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        if (s.orders().kind(i) != OrderKind.ATTACK_MOVE) {
            return false;
        }
        if (goalReachable(s, i)) {
            // a breach has opened the way since this walk's path was made: it goes the new way, once
            if (s.breaches().take(ctx.handle())) {
                s.paths().setRepath(i, true);
                return true;
            }
            return false;
        }
        MotionStore motion = s.motion();
        int faction = s.identity().faction(i);
        BuildingStore buildings = s.buildings();
        int best = -1;
        double nearest = Double.POSITIVE_INFINITY;
        for (int handle : buildings.handles()) {
            int owner = buildings.faction(handle);
            if (owner == faction
                    || s.allies().allied(faction, owner)
                    || owner >= s.config().factions()) {
                continue;
            }
            List<String> classes =
                    s.templates().byIndex(buildings.template(handle)).classes();
            if (!classes.contains(WALL) || !Siege.canStrike(s, i, handle)) {
                continue;
            }
            if (s.breaches().resting(ctx.handle(), handle, s.tick())) {
                continue; // it tried this one a moment ago
            }
            Footprint at = s.footprintOf(handle);
            double toX = at.anchorX() + at.spanX() / 2.0 - motion.x(i);
            double toZ = at.anchorZ() + at.spanZ() / 2.0 - motion.z(i);
            if (toX * (s.orders().targetX(i) - motion.x(i)) + toZ * (s.orders().targetZ(i) - motion.z(i)) <= 0) {
                continue; // behind the unit: not in its way
            }
            double dx = Math.max(Math.max(at.anchorX() - motion.x(i), motion.x(i) - (at.anchorX() + at.spanX())), 0);
            double dz = Math.max(Math.max(at.anchorZ() - motion.z(i), motion.z(i) - (at.anchorZ() + at.spanZ())), 0);
            double d = DetMath.length(dx, dz);
            if (d <= REACH && d < nearest) { // handles ascend: the lower on a tie
                nearest = d;
                best = handle;
            }
        }
        if (best < 0) {
            return false;
        }
        Footprint at = s.footprintOf(best);
        if (!s.orders()
                .pushFront(
                        i,
                        OrderKind.BURN,
                        at.anchorX() + at.spanX() / 2.0,
                        at.anchorZ() + at.spanZ() / 2.0,
                        0,
                        0,
                        best)) {
            return false;
        }
        s.breaches().put(ctx.handle(), best, s.tick() + COOLDOWN);
        s.bus().publish(new OrderReceived(ctx.handle()));
        return true;
    }
}
