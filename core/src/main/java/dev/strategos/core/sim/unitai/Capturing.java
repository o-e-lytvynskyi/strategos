package dev.strategos.core.sim.unitai;

import dev.strategos.core.content.Footprint;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.sim.Arrived;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.PlaceMoved;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.Placing;
import dev.strategos.core.sim.capture.CaptureSystem;
import dev.strategos.core.sim.combat.Engagement;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.sim.unitai.Hfsm.Context;
import java.util.function.IntPredicate;

/**
 * What the CAPTURE states do (CMB-10): the unit takes a free place round the building's footprint, the builders' slots
 * as a burner does ({@link Burning}), walks there and, once within {@value #REACH} blocks of the footprint, adds its
 * {@code Capture/strength} to the building's capture every tick ({@link CaptureSystem}). The order ends, done, when the
 * building is gone, uncapturable, or its owner is the unit's faction or an ally; a unit pushed out of reach walks back to a place.
 */
final class Capturing {
    private Capturing() {}

    /**
     * How far from the footprint's edge, in blocks, beyond the unit's own radius, a capturer is in melee reach. The ring
     * of places lies half a block off the wall, a corner place 0.7.
     */
    static final double REACH = 2.0;

    /**
     * Entering the approach: the free place round the building nearest the unit, else the nearest at all (capturers
     * may share a place), walked to; a building that cannot be captured ends the order.
     */
    static void approach(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        OrderQueues orders = s.orders();
        int target = orders.target(i);
        if (!capturable(s, i, target)) {
            UnitAi.finishOrder(ctx, false);
            return;
        }
        int[] ring = Placing.slots(s, s.footprintOf(target));
        boolean[] taken = new boolean[ring.length];
        for (int j = 0; j < s.entities().highWater(); j++) {
            if (j != i
                    && s.entities().aliveAt(j)
                    && !s.garrisoned(j)
                    && orders.kind(j) == OrderKind.CAPTURE
                    && orders.target(j) == target
                    && orders.slot(j) > 0
                    && orders.slot(j) <= ring.length) {
                taken[orders.slot(j) - 1] = true;
            }
        }
        IntPredicate standable = Constructing.standable(s, target);
        MotionStore m = s.motion();
        int best = nearest(ring, taken, standable, m.x(i), m.z(i), true);
        if (best < 0) {
            best = nearest(ring, taken, standable, m.x(i), m.z(i), false);
        }
        if (best < 0) {
            UnitAi.finishOrder(ctx, true); // no ground round it
            return;
        }
        orders.retarget(i, target, best + 1);
        orders.setPlace(i, (ring[best] >>> 16) + 0.5, (ring[best] & 0xFFFF) + 0.5);
        s.bus().publish(new PlaceMoved(ctx.handle()));
    }

    /** The index of the standable place nearest (x, z), the lower on a tie, skipping taken ones when asked; -1 for none. */
    private static int nearest(int[] ring, boolean[] taken, IntPredicate standable, double x, double z, boolean free) {
        int best = -1;
        double bestD = Double.POSITIVE_INFINITY;
        for (int k = 0; k < ring.length; k++) {
            if ((free && taken[k]) || !standable.test(ring[k])) {
                continue;
            }
            double d = DetMath.length((ring[k] >>> 16) + 0.5 - x, (ring[k] & 0xFFFF) + 0.5 - z);
            if (d < bestD) {
                bestD = d;
                best = k;
            }
        }
        return best;
    }

    /** The approach's think: a building lost on the way ends the order; within reach of it, capturing. */
    static void keepApproaching(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        int target = s.orders().target(i);
        if (!capturable(s, i, target)) {
            UnitAi.finishOrder(ctx, false);
        } else if (inReach(s, i, target)) {
            ctx.go(UnitAi.CAPTURING);
        }
    }

    /** Arrived at the place: capturing if within reach, else on again; arriving short twice on one spot ends the order. */
    static void arrived(Context<MatchState> ctx, Arrived m) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        if (inReach(s, i, s.orders().target(i))) {
            ctx.go(UnitAi.CAPTURING);
            return;
        }
        MotionStore motion = s.motion();
        int here = ((int) (motion.x(i) * 8) << 16) | (int) (motion.z(i) * 8);
        double lastX = (ctx.data1() >>> 16) / 8.0;
        double lastZ = (ctx.data1() & 0xFFFF) / 8.0;
        if (ctx.data1() != 0 && DetMath.length(motion.x(i) - lastX, motion.z(i) - lastZ) < Burning.AGAIN) {
            UnitAi.finishOrder(ctx, true); // arrived again where it arrived last: the way gets no nearer
        } else {
            ctx.setData(ctx.data0(), here);
            s.bus().publish(new PlaceMoved(ctx.handle()));
        }
    }

    /**
     * CAPTURING's think, every tick: the order ends when it can no longer be carried out; a unit out of reach walks to
     * a place again; else its strength goes to the building's capture.
     */
    static void work(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        int target = s.orders().target(i);
        if (!capturable(s, i, target)) {
            UnitAi.finishOrder(ctx, false);
        } else if (!inReach(s, i, target)) {
            ctx.go(UnitAi.CAPTURE);
        } else {
            s.play().capture().contribute(target, s.identity().faction(i), CaptureSystem.strength(s, i));
        }
    }

    /**
     * Whether unit {@code i} can still capture {@code target}: a standing building with capture points, owned by a
     * faction that is not the unit's own nor its ally, and a unit with strength. The command decides the rest.
     */
    static boolean capturable(MatchState s, int i, int target) {
        BuildingStore b = s.buildings();
        return BuildingStore.isBuilding(target)
                && b.alive(target)
                && CaptureSystem.total(s, target) > 0
                && CaptureSystem.strength(s, i) > 0
                && !s.allies().allied(s.identity().faction(i), b.faction(target));
    }

    /** Whether unit {@code i} stands within {@value #REACH} blocks (and its radius) of {@code target}'s footprint. */
    static boolean inReach(MatchState s, int i, int target) {
        Footprint at = s.footprintOf(target);
        MotionStore m = s.motion();
        double dx = m.x(i) - Math.max(at.anchorX(), Math.min(m.x(i), at.anchorX() + at.spanX()));
        double dz = m.z(i) - Math.max(at.anchorZ(), Math.min(m.z(i), at.anchorZ() + at.spanZ()));
        return DetMath.length(dx, dz) <= Engagement.radius(s, i) + REACH
                && Math.abs(m.y(i) - (s.buildings().baseY(target) + 1)) <= Constructing.SLOT_RISE + 1;
    }
}
