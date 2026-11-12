package dev.strategos.core.sim.unitai;

import dev.strategos.core.shared.DetMath;
import dev.strategos.core.sim.Arrived;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.OrderFinished;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.PlaceMoved;
import dev.strategos.core.sim.build.Placing;
import dev.strategos.core.sim.garrison.Garrisons;
import dev.strategos.core.sim.unitai.Hfsm.Context;

/**
 * What the GARRISON states do (BLD-16): walk to the order's building's door; once within reach of it and with room,
 * go in. The order is then done and the unit rests in GARRISONED with the orders behind it kept; they resume when it
 * comes out ({@link Garrisons#release}). A building that is gone, unfinished, another faction's or full ends the order
 * as failed.
 */
final class Garrisoning {
    private Garrisoning() {}

    /** Entering the approach: the door, walked to; a building that cannot take the unit, or no door, fails the order. */
    static void approach(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        int building = s.orders().target(i);
        if (!Garrisons.admits(s, i, building) || Garrisons.full(s, building)) {
            UnitAi.finishOrder(ctx, true);
            return;
        }
        int[] door = Garrisons.door(s, building);
        if (door == null) {
            UnitAi.finishOrder(ctx, true); // walled in
            return;
        }
        s.orders().setPlace(i, door[0] + 0.5, door[1] + 0.5);
        if (near(s, i)) {
            enter(ctx);
        } else {
            s.bus().publish(new PlaceMoved(ctx.handle()));
        }
    }

    /** The approach's think: a building lost or full on the way fails the order; within reach of the door, in. */
    static void keepApproaching(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        int building = s.orders().target(i);
        if (!Garrisons.admits(s, i, building)) {
            UnitAi.finishOrder(ctx, false);
        } else if (near(s, i)) {
            enter(ctx);
        }
    }

    /** Arrived: in if within reach of the door, else walk on (a crowd may have stopped the unit short). */
    static void arrived(Context<MatchState> ctx, Arrived m) {
        if (near(ctx.host(), ctx.unit())) {
            enter(ctx);
        } else {
            ctx.host().bus().publish(new PlaceMoved(ctx.handle()));
        }
    }

    /** Goes in: the unit joins the occupants, its order is done, and it rests with the others behind it kept. */
    private static void enter(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        int building = s.orders().target(i);
        if (Garrisons.full(s, building)) {
            UnitAi.finishOrder(ctx, true); // the last place went to another
            return;
        }
        s.garrison().add(i, ctx.handle(), building, s.orders().slot(i) == Garrisons.BELL_SLOT);
        s.releaseClaim(i, ctx.handle());
        s.paths().clear(i);
        s.motion().setVelocity(i, 0, 0);
        s.motion().setLatch(i, false, 0, 0);
        s.orders().pop(i);
        s.bus().publish(new OrderFinished(ctx.handle(), false));
        ctx.go(UnitAi.GARRISONED);
    }

    /** GARRISONED hears an order: while inside it waits in the queue; once let out, the unit takes up its queue. */
    static void orderReceived(Context<MatchState> ctx, OrderReceived m) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        if (s.garrison().inside(i)) {
            return;
        }
        if (s.orders().size(i) == 0) {
            ctx.go(UnitAi.IDLE);
        } else {
            UnitAi.startOrder(ctx);
        }
    }

    /** Within reach of the door's place on the plane and within a step of its floor, so not on a roof or in a pit. */
    private static boolean near(MatchState s, int i) {
        double tx = s.orders().targetX(i);
        double tz = s.orders().targetZ(i);
        if (DetMath.length(s.motion().x(i) - tx, s.motion().z(i) - tz) > Garrisons.REACH) {
            return false;
        }
        double floor =
                Placing.standFloor(s.surfacesFor(i), s.config().bounds(), (int) Math.floor(tx), (int) Math.floor(tz));
        return Double.isNaN(floor) || Math.abs(s.motion().y(i) - floor) <= Garrisons.RISE;
    }
}
