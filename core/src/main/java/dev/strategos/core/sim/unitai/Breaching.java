package dev.strategos.core.sim.unitai;

import dev.strategos.core.content.Footprint;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.combat.Siege;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.unitai.Hfsm.Context;
import java.util.List;

/**
 * An attack-move that finds a wall in its way (BLD-13, M9 spec §2.10): its goal is unreachable for its faction, so its
 * path ended at the nearest point it could reach and it has no way nearer. Instead of giving the order up it attacks
 * the nearest wall segment of another faction within {@value #REACH} blocks, as an attack on any building is made
 * (torches, rams: CMB-03, CMB-09), and goes on with the order when the segment has fallen: through the gap, or to the
 * next segment, until nothing blocks it or nothing it can strike is near.
 */
final class Breaching {
    /** The class the segments of a wall have. */
    static final String WALL = "Wall";

    /** How far from itself, in blocks to a segment's nearest column, a unit looks for the wall that blocks it. */
    static final double REACH = 6;

    /** A goal this near counts as reached (a crowd's places lie round it). */
    static final double SHORT = 3;

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

    /** Puts the attack on the nearest wall segment before the unit's attack-move; false when there is none to strike. */
    static boolean breach(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        if (s.orders().kind(i) != OrderKind.ATTACK_MOVE) {
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
            Footprint at = s.footprintOf(handle);
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
        s.bus().publish(new OrderReceived(ctx.handle()));
        return true;
    }
}
