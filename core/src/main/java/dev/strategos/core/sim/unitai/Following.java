package dev.strategos.core.sim.unitai;

import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.SlotLayout;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.PlaceMoved;
import dev.strategos.core.sim.combat.Engagement;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.sim.unitai.Hfsm.Context;

/**
 * What the FOLLOW states do (CAM-07): keep a place behind the commander the order names, in rows across its facing
 * ({@link SlotLayout}, the move's layout), the front row {@value #BEHIND} blocks behind it. A follower walks on while
 * its place moves and waits where it stands once there; a leader back in the RTS view leaves its followers where they
 * are.
 */
final class Following {
    static final double BEHIND = 2.0;
    /** How far a waiting follower's place must move before it walks again, in blocks. */
    static final double REGOAL = 1.5;

    private Following() {}

    /** FOLLOW_WALKING's think ({@code walking}) and FOLLOW_WAITING's: the place behind the leader, walked to. */
    static void keepUp(Context<MatchState> ctx, boolean walking) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        OrderQueues orders = s.orders();
        int leader = orders.target(i);
        if (!s.leaders().leads(leader)) {
            return;
        }
        int group = orders.group(i);
        int n = 1;
        if (group != 0) {
            n = 0;
            for (int j = 0; j < s.entities().highWater(); j++) {
                if (s.entities().aliveAt(j)
                        && orders.kind(j) == OrderKind.FOLLOW
                        && orders.group(j) == group
                        && orders.target(j) == leader) {
                    n++;
                }
            }
        }
        double yaw = Math.toRadians(s.leaders().yaw(leader));
        double fx = -DetMath.sin(yaw);
        double fz = DetMath.cos(yaw);
        double spacing = SlotLayout.spacing(Engagement.radius(s, i));
        int width = SlotLayout.width(n);
        int rows = (n + width - 1) / width;
        double back = BEHIND + (rows - 1) * spacing / 2;
        double cx = s.leaders().x(leader) - fx * back;
        double cz = s.leaders().z(leader) - fz * back;
        int k = Math.min(orders.slot(i), n - 1);
        double px = SlotLayout.x(k, n, width, spacing, cx, fx, fz);
        double pz = SlotLayout.z(k, n, width, spacing, cz, fx, fz);
        MotionStore motion = s.motion();
        double moved = DetMath.length(px - orders.targetX(i), pz - orders.targetZ(i));
        double away = DetMath.length(px - motion.x(i), pz - motion.z(i));
        if (moved > (walking ? Math.max(Melee.REGOAL, away / 4) : REGOAL)) {
            orders.setPlace(i, px, pz);
            s.bus().publish(new PlaceMoved(ctx.handle()));
            if (!walking) {
                ctx.go(UnitAi.FOLLOW_WALKING);
            }
        }
    }
}
