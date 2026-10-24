package dev.strategos.core.sim.unitai;

import dev.strategos.core.content.Footprint;
import dev.strategos.core.protocol.NoticeKind;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.sim.Arrived;
import dev.strategos.core.sim.MatchBounds;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.Notice;
import dev.strategos.core.sim.PlaceMoved;
import dev.strategos.core.sim.econ.CarryStore;
import dev.strategos.core.sim.econ.Deposits;
import dev.strategos.core.sim.econ.Dropsites;
import dev.strategos.core.sim.econ.Felling;
import dev.strategos.core.sim.econ.Fields;
import dev.strategos.core.sim.econ.Forage;
import dev.strategos.core.sim.econ.GatherRates;
import dev.strategos.core.sim.econ.GatherSlots;
import dev.strategos.core.sim.econ.Livestock;
import dev.strategos.core.sim.econ.NodeStore;
import dev.strategos.core.sim.econ.Quarrying;
import dev.strategos.core.sim.nav.MaterialKind;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.sim.unitai.Hfsm.Context;
import java.util.function.IntPredicate;

/**
 * What the GATHER states do (ECO-05, 04 §11.4): take a free work slot at the order's node, walk to it, gather at the
 * unit's rate in a fixed-point remainder until the load is full, carry it to the faction's nearest dropsite that takes
 * it, and walk back. A node that runs out is replaced by the nearest of its subtype within {@value #RETARGET} blocks of
 * it with a free slot; with none, a unit carrying something takes it home first, and then stands idle with a notice.
 * The order's target is the node's id, 0 once there is none; the slot is the unit's from claiming it until it leaves
 * GATHER. Finding the next node is a step, not a state.
 */
final class Gathering {
    /** How far from a spent node the next of its subtype may be, in blocks (ECO-05.2). */
    static final double RETARGET = 24;
    /** How far from its slot's centre an arrived unit may stand and still work, in blocks. */
    static final double WORK_REACH = 1.5;

    private static final long TICKS_PER_SECOND = 20;

    private Gathering() {}

    /**
     * Entering GATHER's approach: drop a load of another resource, claim a free slot at the node (one the unit holds is
     * kept) and walk to it; a spent node, or one with no room, is replaced first.
     */
    static void approach(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        int node = s.orders().target(i);
        if (!usable(s, i, ctx.handle(), node)) {
            next(ctx, node);
            return;
        }
        NodeStore nodes = s.nodes();
        CarryStore carry = s.carry();
        if (carry.resource(i) != CarryStore.NONE
                && (carry.resource(i) != nodes.resource(node) || carry.subtype(i) != nodes.subtype(node))) {
            s.dropLoad(i); // a load of something else is lost, as in the games
        }
        if (carry.resource(i) != CarryStore.NONE
                && carry.amount(i) >= s.gatherRates().capacity(s.stats(), i, carry.resource(i))) {
            ctx.go(UnitAi.GATHER_RETURNING); // back to work with a full load, after a flight say: home first
            return;
        }
        if (carry.claimed(i) != node) {
            s.releaseClaim(i, ctx.handle());
        }
        MotionStore m = s.motion();
        int slot = nodes.claimNearest(node, ctx.handle(), m.x(i), m.z(i), standable(s, node));
        carry.setClaimed(i, node);
        int cell = nodes.slot(node, slot);
        s.orders().retarget(i, node, slot);
        s.orders().setPlace(i, (cell >>> 16) + 0.5, (cell & 0xFFFF) + 0.5);
        s.bus().publish(new PlaceMoved(ctx.handle()));
    }

    /** The approach's think: a node spent on the way is replaced. */
    static void keepApproaching(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int node = s.orders().target(ctx.unit());
        if (node == 0 || s.nodes().exhausted(node)) {
            next(ctx, node);
        }
    }

    /** Arrived at the slot: work, or walk on if the crowd stopped the unit short of it. */
    static void arrived(Context<MatchState> ctx, Arrived m) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        OrderQueues orders = s.orders();
        double d =
                DetMath.length(s.motion().x(i) - orders.targetX(i), s.motion().z(i) - orders.targetZ(i));
        if (d <= WORK_REACH) {
            ctx.go(UnitAi.GATHERING);
        } else {
            s.bus().publish(new PlaceMoved(ctx.handle())); // the place is free again by now, or the ladder fails it
        }
    }

    /** Entering GATHERING: the unit faces its node; the first data word is the remainder, in thousandths × 20. */
    static void begin(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        int node = s.orders().target(i);
        if (node == 0) {
            return;
        }
        MotionStore m = s.motion();
        double dx = s.nodes().anchorX(node) - m.x(i);
        double dz = s.nodes().anchorZ(node) - m.z(i);
        if (dx == 0 && dz == 0) {
            return;
        }
        float yaw = DetMath.yawDegrees(dx, dz);
        if (yaw != m.yaw(i)) {
            m.set(i, m.x(i), m.y(i), m.z(i), yaw);
            s.frame().add(ctx.handle(), m.x(i), m.y(i), m.z(i), yaw, (byte) 0);
        }
    }

    /**
     * GATHERING's think, every tick: the rate a second adds to the remainder, and each whole thousandth of it is taken
     * from the node, up to what the unit can still carry; a full load goes home, a spent node is replaced.
     */
    static void work(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        NodeStore nodes = s.nodes();
        int node = s.orders().target(i);
        if (node == 0 || nodes.exhausted(node)) {
            next(ctx, node);
            return;
        }
        int resource = nodes.resource(node);
        int subtype = nodes.subtype(node);
        GatherRates rates = s.gatherRates();
        CarryStore carry = s.carry();
        long room = rates.capacity(s.stats(), i, resource) - carry.amount(i);
        if (room <= 0) {
            ctx.go(UnitAi.GATHER_RETURNING);
            return;
        }
        if (nodes.kind(node) == MaterialKind.CARCASS && Livestock.alive(s, node)) { // slaughtered first (ECO-16)
            ctx.setData(ctx.data0(), Livestock.strike(s, i, ctx.handle(), node, ctx.data1()));
            return;
        }
        long rate = rates.rate(s.stats(), i, resource, subtype);
        if (nodes.kind(node) == MaterialKind.CROP) { // a field's farmers share in diminishing returns (ECO-09)
            rate = Math.round(rate * Fields.share(Fields.farmers(nodes, node)));
        }
        long acc = ctx.data0() + rate;
        long whole = acc / TICKS_PER_SECOND;
        ctx.setData((int) (acc - whole * TICKS_PER_SECOND), 0);
        long want = Math.min(whole, room);
        if (want > 0) {
            long taken = switch (nodes.kind(node)) {
                case LOG -> Felling.take(s, node, want);
                case STONE -> Quarrying.take(s, node, want);
                case DEPOSIT -> Deposits.take(s, node, want);
                case CARCASS -> Livestock.take(s, node, want);
                case CROP -> Fields.take(s, node, want);
                default -> Forage.take(s, node, want);
            };
            if (taken > 0) {
                carry.add(i, resource, subtype, taken);
                s.ledger().taken(resource, taken);
            }
        }
        if (carry.amount(i) >= rates.capacity(s.stats(), i, resource)) {
            ctx.go(UnitAi.GATHER_RETURNING);
        } else if (nodes.exhausted(node)) {
            next(ctx, node);
        }
    }

    /**
     * Entering the way home: the nearest dropsite that takes the load, its handle the first data word, and the cell
     * beside its footprint nearest the unit; with none, the unit keeps its load and stands idle with a notice.
     */
    static void headHome(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        int resource = s.carry().resource(i);
        if (resource == CarryStore.NONE) {
            backToWork(ctx);
            return;
        }
        int faction = s.identity().faction(i);
        MotionStore m = s.motion();
        int drop = Dropsites.nearest(s, faction, resource, m.x(i), m.z(i));
        if (drop == 0) {
            idle(ctx, "no dropsite");
            return;
        }
        ctx.setData(drop, 0);
        double[] at = Dropsites.dropPoint(s, drop, m.x(i), m.z(i));
        MatchBounds b = s.config().bounds();
        s.orders().setPlace(i, clamp(at[0], b.sizeX()), clamp(at[1], b.sizeZ()));
        s.bus().publish(new PlaceMoved(ctx.handle()));
    }

    /** The way home's think: a dropsite lost on the way, or no longer taking the load, is replaced. */
    static void keepHeadingHome(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        int resource = s.carry().resource(i);
        if (resource == CarryStore.NONE
                || !Dropsites.takes(s, ctx.data0(), s.identity().faction(i), resource)) {
            ctx.go(UnitAi.GATHER_RETURNING);
        }
    }

    /** Home: in reach of the dropsite the load goes to the treasury and the unit goes back; else it walks on. */
    static void delivered(Context<MatchState> ctx, Arrived m) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        int drop = ctx.data0();
        int faction = s.identity().faction(i);
        int resource = s.carry().resource(i);
        if (resource == CarryStore.NONE || !Dropsites.takes(s, drop, faction, resource)) {
            ctx.go(UnitAi.GATHER_RETURNING);
            return;
        }
        Footprint at = s.footprintOf(drop);
        MotionStore motion = s.motion();
        if (Dropsites.distance(at, motion.x(i), motion.z(i)) > Dropsites.REACH) {
            s.bus().publish(new PlaceMoved(ctx.handle()));
            return;
        }
        long load = s.carry().clear(i);
        s.treasury().gather(faction, resource, load);
        backToWork(ctx);
    }

    /** After a delivery: back to the node, or the next one, or idle. */
    private static void backToWork(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        int node = s.orders().target(i);
        if (usable(s, i, ctx.handle(), node)) {
            ctx.go(UnitAi.GATHER_APPROACHING);
        } else {
            next(ctx, node);
        }
    }

    /**
     * The unit's node {@code spent} ran out or has no room: the nearest of its subtype within {@value #RETARGET} blocks
     * of it with room takes its place; with none, a unit with a load takes it home and then stands, one without stands
     * now, each with a notice.
     */
    static void next(Context<MatchState> ctx, int spent) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        s.releaseClaim(i, ctx.handle());
        int found = 0;
        if (spent != 0) {
            NodeStore nodes = s.nodes();
            int handle = ctx.handle();
            found = s.nodeIndex()
                    .nearest(
                            nodes,
                            nodes.resource(spent),
                            nodes.subtype(spent),
                            nodes.anchorX(spent),
                            nodes.anchorZ(spent),
                            RETARGET,
                            id -> id != spent && usable(s, i, handle, id));
        }
        OrderQueues orders = s.orders();
        if (found != 0) {
            orders.retarget(i, found, 0);
            ctx.go(UnitAi.GATHER_APPROACHING);
            return;
        }
        orders.retarget(i, 0, 0);
        if (s.carry().resource(i) != CarryStore.NONE && !ctx.in(UnitAi.GATHER_RETURNING)) {
            ctx.go(UnitAi.GATHER_RETURNING); // home with what it has; then nothing to go back to
            return;
        }
        idle(ctx, "no node");
    }

    /** The order ends, with an idle-worker notice to the faction. */
    private static void idle(Context<MatchState> ctx, String why) {
        MatchState s = ctx.host();
        s.outputs()
                .add(new Notice(
                        new FactionSlot(s.identity().faction(ctx.unit())), NoticeKind.IDLE_WORKER, ctx.handle(), why));
        UnitAi.finishOrder(ctx, false);
    }

    /** Leaving GATHER: the slot is given up. */
    static void leave(Context<MatchState> ctx) {
        ctx.host().releaseClaim(ctx.unit(), ctx.handle());
    }

    /** Whether unit {@code i} can work node {@code node}: it holds some, and a slot is free or the unit's own. */
    static boolean usable(MatchState s, int i, int handle, int node) {
        NodeStore nodes = s.nodes();
        if (node < 1 || node > nodes.count() || nodes.exhausted(node)) {
            return false;
        }
        return s.gatherRates().gathers(s.stats(), i, nodes.resource(node), nodes.subtype(node))
                && nodes.hasRoom(node, handle, standable(s, node));
    }

    /** A slot cell a unit can stand on ({@link GatherSlots#standable}). */
    static IntPredicate standable(MatchState s, int node) {
        return GatherSlots.standable(s, node);
    }

    private static double clamp(double v, int size) {
        return Math.max(0.5, Math.min(size - 0.5, v));
    }
}
