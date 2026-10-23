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
import dev.strategos.core.sim.combat.CombatSystem;
import dev.strategos.core.sim.combat.Damage;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.sim.unitai.Hfsm.Context;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * What the BURN states do (CMB-03, D-12): an attack on a building puts a torch in the attacker's hand; it takes a free
 * place round the footprint, the builders' slots, walks there and, after its torch's wind-up, deals its
 * {@code Attack/Structure/fire} every {@code Attack/Structure/repeat} ticks, against the building's {@code
 * Resistance/fire}, each blow showing flames on the nearest of its blocks, never a fire block. It burns until the
 * building falls or the order ends.
 */
final class Burning {
    static final String FIRE = CombatSystem.TORCH_FIRE;
    static final String PREPARE = CombatSystem.TORCH_PREPARE;
    static final String REPEAT = CombatSystem.TORCH_REPEAT;

    private Burning() {}

    /** Whether unit {@code i} carries a torch: its {@code Attack/Structure/fire} above 0. */
    static boolean canBurn(MatchState s, int i) {
        return value(s, i, FIRE, 0) > 0;
    }

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

    /** The approach's think: a building gone on the way ends the order. */
    static void keepApproaching(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        if (!burnable(s, ctx.unit(), s.orders().target(ctx.unit()))) {
            UnitAi.finishOrder(ctx, false);
        }
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
        ctx.setData((int) s.tick() + (int) value(s, i, PREPARE, 0), 0);
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
        s.queueDamage(new Damage(target, ctx.handle(), 0, 0, 0, (float) value(s, i, FIRE, 0), List.of(), false));
        s.outputs().add(new UnitStruck(ctx.handle(), target));
        Footprint at = s.footprintOf(target);
        MotionStore m = s.motion();
        int x = (int) Math.max(at.anchorX(), Math.min(at.anchorX() + at.spanX() - 1, Math.floor(m.x(i))));
        int z = (int) Math.max(at.anchorZ(), Math.min(at.anchorZ() + at.spanZ() - 1, Math.floor(m.z(i))));
        int height = s.blueprintOf(target).sizeY();
        s.outputs().add(new FlameShown(x, s.buildings().baseY(target) + 1 + Math.min(2, height - 1), z));
        ctx.setData(tick + Math.max(1, (int) value(s, i, REPEAT, 20)), 0);
    }

    /** Whether unit {@code i} can still burn {@code target}: a standing building of another faction, and a torch. */
    static boolean burnable(MatchState s, int i, int target) {
        BuildingStore b = s.buildings();
        return BuildingStore.isBuilding(target)
                && b.alive(target)
                && b.faction(target) != s.identity().faction(i)
                && canBurn(s, i);
    }

    private static double value(MatchState s, int i, String path, double otherwise) {
        int p = s.stats().path(path);
        double v = p < 0 ? Double.NaN : s.stats().read(i, p);
        return Double.isFinite(v) && v >= 0 ? v : otherwise;
    }
}
