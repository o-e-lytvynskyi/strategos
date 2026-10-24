package dev.strategos.core.sim.unitai;

import dev.strategos.core.content.Footprint;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.sim.Arrived;
import dev.strategos.core.sim.MatchBounds;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.PlaceMoved;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.Construction;
import dev.strategos.core.sim.build.Placing;
import dev.strategos.core.sim.nav.SurfaceGrid;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.sim.unitai.Hfsm.Context;
import java.util.function.IntPredicate;

/**
 * What the BUILD states do (BLD-03, 04 §11.7): take a free builder slot round the order's building (foundation or
 * site) and walk to it; the first to arrive commits a foundation; then each tick a builder adds its rate to the site's
 * work, until it stands finished, is gone, or is its faction's no more. The slot is the unit's from claiming it until it
 * leaves BUILD.
 */
final class Constructing {
    /** How far from its slot's centre an arrived builder may stand and still build, in blocks. */
    static final double BUILD_REACH = 1.5;
    /** How far from the site's floor a slot's floor may be, in blocks. */
    static final double SLOT_RISE = 2;

    private Constructing() {}

    /** Entering the approach: a free slot round the building, walked to; none, or a building done, ends the order. */
    static void approach(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        int site = s.orders().target(i);
        if (!workable(s, i, site)) {
            UnitAi.finishOrder(ctx, false);
            return;
        }
        BuildingStore buildings = s.buildings();
        if (s.carry().site(i) != site) {
            s.releaseClaim(i, ctx.handle());
        }
        MotionStore m = s.motion();
        int slot = buildings.claimNearest(site, ctx.handle(), m.x(i), m.z(i), standable(s, site));
        if (slot < 0) {
            UnitAi.finishOrder(ctx, true); // every slot taken or no ground round it
            return;
        }
        s.carry().setSite(i, site);
        int cell = buildings.slot(site, slot);
        s.orders().retarget(i, site, slot);
        s.orders().setPlace(i, (cell >>> 16) + 0.5, (cell & 0xFFFF) + 0.5);
        s.bus().publish(new PlaceMoved(ctx.handle()));
    }

    /** The approach's think: a building finished or lost on the way ends the order. */
    static void keepApproaching(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        if (!workable(s, ctx.unit(), s.orders().target(ctx.unit()))) {
            UnitAi.finishOrder(ctx, false);
        }
    }

    /** Arrived at the slot: build, or walk on if the crowd stopped the unit short of it. */
    static void arrived(Context<MatchState> ctx, Arrived m) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        OrderQueues orders = s.orders();
        double d =
                DetMath.length(s.motion().x(i) - orders.targetX(i), s.motion().z(i) - orders.targetZ(i));
        if (d <= BUILD_REACH) {
            ctx.go(UnitAi.BUILDING);
        } else {
            s.bus().publish(new PlaceMoved(ctx.handle()));
        }
    }

    /** Entering BUILDING: the first builder commits a foundation; the builder faces the footprint's middle. */
    static void begin(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        int site = s.orders().target(i);
        if (!workable(s, i, site)) {
            return; // its think ends the order
        }
        Construction.commit(s, site);
        Footprint at = s.footprintOf(site);
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

    /** BUILDING's think, every tick: the builder's rate joins the site's work of the tick, until there is none to do. */
    static void work(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        int site = s.orders().target(i);
        if (!workable(s, i, site)) {
            UnitAi.finishOrder(ctx, false);
            return;
        }
        int path = s.stats().path(Construction.RATE);
        double rate = path < 0 ? Double.NaN : s.stats().read(i, path);
        s.buildings().contribute(site, Double.isFinite(rate) && rate > 0 ? rate : 1);
    }

    /** Leaving BUILD: the slot is given up. */
    static void leave(Context<MatchState> ctx) {
        ctx.host().releaseClaim(ctx.unit(), ctx.handle());
    }

    /**
     * Whether unit {@code i} can still build {@code site}: its faction's, standing, not yet finished or damaged (a
     * repair, BLD-06), and it builds.
     */
    static boolean workable(MatchState s, int i, int site) {
        BuildingStore b = s.buildings();
        return b.alive(site)
                && b.faction(site) == s.identity().faction(i)
                && (b.state(site) != BuildingStore.COMPLETE || b.damage(site) > 0)
                && s.stats().canBuild(i);
    }

    /** A slot cell a builder can stand on: a floor within {@value #SLOT_RISE} blocks of the site's, no building there. */
    static IntPredicate standable(MatchState s, int site) {
        SurfaceGrid grid = s.surfaces();
        MatchBounds bounds = s.config().bounds();
        double base = s.buildings().baseY(site) + 1;
        return cell -> {
            int x = cell >>> 16;
            int z = cell & 0xFFFF;
            if (s.footprintAt(x, z)) {
                return false;
            }
            double floor = Placing.floor(grid, bounds, x, z);
            return !Double.isNaN(floor) && Math.abs(floor - base) <= SLOT_RISE;
        };
    }
}
