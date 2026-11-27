package dev.strategos.core.sim.unitai;

import dev.strategos.core.content.AbilityDef;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.Arrived;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.PlaceMoved;
import dev.strategos.core.sim.ability.AbilityRules;
import dev.strategos.core.sim.combat.Engagement;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.sim.unitai.Hfsm.Context;
import java.util.List;

/**
 * What the ABILITY state does (UNIT-10): the unit's order names an ability (the queue's slot: its index in the template
 * set), a unit it is aimed at (the target) or a point (the place). Each tick the unit asks whether the target is in the
 * ability's range, as an attack asks whether its target is in reach: if so, the ability fires through {@link
 * AbilityRules#fire} (cooldown, cost and effects checked then, the cost paid once) and the order is done; if not, the
 * unit walks to the target, whose place it follows as a chaser follows its quarry, until it is. An ability that is
 * refused for any other reason, or whose target is gone, fails the order; the queue goes on.
 */
final class UsingAbility {
    private UsingAbility() {}

    /** Entering: an ability whose target is already in range fires at once. */
    static void enter(Context<MatchState> ctx) {
        attempt(ctx);
    }

    /** ABILITY's think, every tick. */
    static void work(Context<MatchState> ctx) {
        attempt(ctx);
    }

    /** An arrival short of range ends the order, failed: the way gets no nearer. */
    static void arrived(Context<MatchState> ctx, Arrived m) {
        if (!attempt(ctx)) {
            UnitAi.finishOrder(ctx, true);
        }
    }

    /** One go at it; whether the order ended (fired, or failed), which leaves the unit to its next. */
    private static boolean attempt(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        OrderQueues orders = s.orders();
        List<AbilityDef> all = s.templates().abilities();
        int slot = orders.slot(i);
        if (slot < 0 || slot >= all.size()) {
            UnitAi.finishOrder(ctx, true); // an ability the set no longer has
            return true;
        }
        AbilityDef def = all.get(slot);
        int unit = orders.target(i);
        if (unit != Handle.NONE && Engagement.gone(s, unit)) {
            UnitAi.finishOrder(ctx, true);
            return true;
        }
        double px = orders.targetX(i);
        double pz = orders.targetZ(i);
        if (AbilityRules.inRange(s, i, def, unit, px, pz)) {
            RejectReason why = AbilityRules.fire(s, ctx.handle(), def, unit, px, pz);
            UnitAi.finishOrder(ctx, why != null);
            return true;
        }
        if (unit != Handle.NONE) {
            follow(ctx, unit);
        }
        return false;
    }

    /** The place of a unit the ability is aimed at moves with it, as a chaser's does. */
    private static void follow(Context<MatchState> ctx, int unit) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        int t = Handle.index(unit);
        MotionStore motion = s.motion();
        OrderQueues orders = s.orders();
        double px = motion.x(t);
        double pz = motion.z(t);
        double moved = DetMath.length(px - orders.targetX(i), pz - orders.targetZ(i));
        double away = DetMath.length(px - motion.x(i), pz - motion.z(i));
        if (s.paths().request(i) == 0 && moved > Math.max(Melee.REGOAL, away / 4)) {
            orders.setPlace(i, px, pz);
            s.bus().publish(new PlaceMoved(ctx.handle()));
        }
    }
}
