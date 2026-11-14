package dev.strategos.core.sim.unitai;

import dev.strategos.core.content.Footprint;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.sim.Arrived;
import dev.strategos.core.sim.FlameShown;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.PlaceMoved;
import dev.strategos.core.sim.UnitStruck;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.Placing;
import dev.strategos.core.sim.combat.Engagement;
import dev.strategos.core.sim.combat.Siege;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.sim.unitai.Hfsm.Context;
import java.util.function.IntPredicate;

/**
 * What the BURN states do (CMB-03, D-12): an attack on a building puts a torch in the attacker's hand; it takes a free
 * place round the footprint, the builders' slots, walks there and, after its torch's wind-up, deals its
 * {@code Attack/Structure/fire} every {@code Attack/Structure/repeat} ticks, against the building's {@code
 * Resistance/fire}, each blow showing flames on the nearest of its blocks, never a fire block. It burns until the
 * building falls or the order ends. A unit with no torch but a crush in its melee attack, a ram (CMB-09), takes the same
 * states and strikes with that, on its melee attack's timing, showing no flame ({@link Siege}).
 */
final class Burning {
    private Burning() {}

    /**
     * Entering the approach: the free place round the building nearest the unit, walked to; none, or a building gone,
     * ends the order. The places are the ring round the footprint ({@link Placing#slots}), computed afresh; a place is
     * taken while another burner's current order holds its number, one up (0 for none), so nothing new is stored.
     */
    static void approach(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        OrderQueues orders = s.orders();
        int target = orders.target(i);
        if (!burnable(s, i, target)) {
            UnitAi.finishOrder(ctx, false);
            return;
        }
        int[] ring = Placing.slots(s, s.footprintOf(target));
        boolean[] taken = new boolean[ring.length];
        for (int j = 0; j < s.entities().highWater(); j++) {
            if (j != i
                    && s.entities().aliveAt(j)
                    && !s.garrisoned(j)
                    && orders.kind(j) == OrderKind.BURN
                    && orders.target(j) == target
                    && orders.slot(j) > 0
                    && orders.slot(j) <= ring.length) {
                taken[orders.slot(j) - 1] = true;
            }
        }
        IntPredicate standable = Constructing.standable(s, target);
        MotionStore m = s.motion();
        int best = -1;
        double bestD = Double.POSITIVE_INFINITY;
        for (int k = 0; k < ring.length; k++) {
            if (taken[k] || !standable.test(ring[k])) {
                continue;
            }
            double d = DetMath.length((ring[k] >>> 16) + 0.5 - m.x(i), (ring[k] & 0xFFFF) + 0.5 - m.z(i));
            if (d < bestD) { // ascending: the lower on a tie
                bestD = d;
                best = k;
            }
        }
        if (best < 0) {
            UnitAi.finishOrder(ctx, true); // every place taken or no ground round it
            return;
        }
        orders.retarget(i, target, best + 1);
        orders.setPlace(i, (ring[best] >>> 16) + 0.5, (ring[best] & 0xFFFF) + 0.5);
        s.bus().publish(new PlaceMoved(ctx.handle()));
    }

    /**
     * QA-12: ticks without progress after which a unit within {@code Constructing.BUILD_REACH} of its place burns from
     * where it is. A crowd at the wall pushes the burners about, and one held 1.2 blocks off its place by the others,
     * trying to return, never lands: it drifted there for good.
     */
    static final int PATIENCE = 5;

    /** The approach's think: a building gone on the way ends the order. */
    static void keepApproaching(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        int target = s.orders().target(i);
        if (!burnable(s, i, target)) {
            UnitAi.finishOrder(ctx, false);
        } else if (touching(s, i, target) || (s.motion().stuck(i) >= PATIENCE && atPlace(s, i))) {
            ctx.go(UnitAi.BURNING);
        }
    }

    private static boolean atPlace(MatchState s, int i) {
        OrderQueues orders = s.orders();
        MotionStore m = s.motion();
        return DetMath.length(m.x(i) - orders.targetX(i), m.z(i) - orders.targetZ(i)) <= Constructing.BUILD_REACH;
    }

    /** How far from a wall the body of a large unit may stop and still strike it: blocks past its radius. */
    static final double WALL_REACH = 1.0;

    /**
     * QA-12: a body wider than a ring cell (a ram's, radius 1) cannot stand on the cell it was given, which lies half a
     * block off the wall, and a house or a crowd beside it keeps it wobbling short of that place for good. It strikes
     * from where its body meets the wall, within {@value #WALL_REACH} blocks of it. A torch-bearer's body fits the cell, so
     * its approach is as it was.
     */
    private static boolean touching(MatchState s, int i, int target) {
        double radius = Engagement.radius(s, i);
        if (radius <= 0.5) {
            return false;
        }
        Footprint at = s.footprintOf(target);
        MotionStore m = s.motion();
        double dx = m.x(i) - Math.max(at.anchorX(), Math.min(m.x(i), at.anchorX() + at.spanX()));
        double dz = m.z(i) - Math.max(at.anchorZ(), Math.min(m.z(i), at.anchorZ() + at.spanZ()));
        return DetMath.length(dx, dz) <= radius + WALL_REACH;
    }

    /** Arrived at the place: burn, or walk on if the crowd stopped the unit short of it. */
    static void arrived(Context<MatchState> ctx, Arrived m) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        OrderQueues orders = s.orders();
        double d =
                DetMath.length(s.motion().x(i) - orders.targetX(i), s.motion().z(i) - orders.targetZ(i));
        if (d <= Constructing.BUILD_REACH) {
            ctx.go(UnitAi.BURNING);
        } else {
            s.bus().publish(new PlaceMoved(ctx.handle()));
        }
    }

    /** Entering BURNING: the torch's wind-up starts, the first data word the tick of the first blow; it faces the building. */
    static void begin(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        int target = s.orders().target(i);
        ctx.setData((int) s.tick() + Siege.prepare(s, i), 0);
        if (!s.buildings().alive(target)) {
            return; // its think ends the order
        }
        Footprint at = s.footprintOf(target);
        MotionStore m = s.motion();
        double dx = at.anchorX() + at.spanX() / 2.0 - m.x(i);
        double dz = at.anchorZ() + at.spanZ() / 2.0 - m.z(i);
        if (dx != 0 || dz != 0) {
            float yaw = DetMath.yawDegrees(dx, dz);
            if (yaw != m.yaw(i)) {
                m.set(i, m.x(i), m.y(i), m.z(i), yaw);
                s.frame().add(ctx.handle(), m.x(i), m.y(i), m.z(i), yaw, (byte) 0);
            }
        }
    }

    /** BURNING's think, every tick: on the tick in the first data word a blow of fire, the next a period later. */
    static void work(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        int target = s.orders().target(i);
        if (!burnable(s, i, target)) {
            UnitAi.finishOrder(ctx, false);
            return;
        }
        int tick = (int) s.tick();
        if (tick < ctx.data0()) {
            return;
        }
        s.queueDamage(Siege.blow(s, i, ctx.handle(), target));
        s.outputs().add(new UnitStruck(ctx.handle(), target));
        if (Siege.fire(s, i) > 0) { // a torch shows flames, a log none
            Footprint at = s.footprintOf(target);
            MotionStore m = s.motion();
            int x = (int) Math.max(at.anchorX(), Math.min(at.anchorX() + at.spanX() - 1, Math.floor(m.x(i))));
            int z = (int) Math.max(at.anchorZ(), Math.min(at.anchorZ() + at.spanZ() - 1, Math.floor(m.z(i))));
            int height = s.blueprintOf(target).sizeY();
            s.outputs().add(new FlameShown(x, s.buildings().baseY(target) + 1 + Math.min(2, height - 1), z));
        }
        ctx.setData(tick + Math.max(1, Siege.repeat(s, i)), 0);
    }

    /**
     * Whether unit {@code i} can still strike {@code target}: a standing building, and a torch or a log that may. Whose building it may be is
     * the attack command's to decide (an own one only when forced, ORD-05), so the order is not checked again here.
     */
    static boolean burnable(MatchState s, int i, int target) {
        BuildingStore b = s.buildings();
        return BuildingStore.isBuilding(target) && b.alive(target) && Siege.canStrike(s, i, target);
    }
}
