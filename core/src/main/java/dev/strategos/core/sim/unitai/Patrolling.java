package dev.strategos.core.sim.unitai;

import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.PlaceMoved;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.sim.unitai.Hfsm.Context;

/**
 * What a patrol does at the end of a leg (ORD-07): it is walked and fought as an attack-move, in
 * WALKING_AND_FIGHTING, and where the walk would end the next point becomes the order's place, the first after the
 * last. A leg that cannot be walked is skipped the same way, so the order never ends by itself.
 */
final class Patrolling {
    private Patrolling() {}

    /** The leg the unit has finished or cannot finish is over: the next point is its place, and it walks there. */
    static void nextLeg(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        OrderQueues orders = s.orders();
        int n = orders.anchorCount(i);
        if (n == 0) { // a patrol that holds no points (a damaged save) has nowhere to go
            UnitAi.finishOrder(ctx, false);
            return;
        }
        int leg = (orders.slot(i) + 1) % n;
        Vec2d point = orders.anchor(i, leg);
        orders.setSlot(i, leg);
        orders.setPlace(i, point.x(), point.z());
        s.bus().publish(new PlaceMoved(ctx.handle()));
    }
}
