package dev.strategos.core.sim.unitai;

import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.PlaceMoved;
import dev.strategos.core.sim.ShotRequested;
import dev.strategos.core.sim.combat.Damage;
import dev.strategos.core.sim.combat.Engagement;
import dev.strategos.core.sim.combat.HighGround;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.sim.stats.StatService;
import dev.strategos.core.sim.unitai.Hfsm.Context;
import java.util.List;

/** Ranged use of the existing COMBAT states: approach without a ring, fire an intent, then chase if needed. */
final class Ranged {
    private static final double MARGIN = 0.5;
    private static final double REGOAL = 0.125;

    private Ranged() {}

    /** The range against target {@code t}: the base range grown by the height over it (CMB-16). */
    private static double effectiveRange(MatchState s, int i, int t) {
        return s.stats().rangedRange(i) * HighGround.rangeFactor(s, i, HighGround.advantage(s, i, t));
    }

    static void takePlace(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int target = s.orders().target(ctx.unit());
        if (!s.entities().isAlive(target)) return;
        s.orders().retarget(ctx.unit(), target, -1);
        close(ctx);
    }

    static void close(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        OrderQueues orders = s.orders();
        int target = orders.target(i);
        if (!s.entities().isAlive(target)) {
            CombatActions.findNext(ctx);
            return;
        }
        int t = Handle.index(target);
        double gap = Engagement.gap(s, i, t);
        if (orders.kind(i) == OrderKind.ENGAGE && gap > s.stats().vision(i)) {
            UnitAi.finishOrder(ctx, false);
            return;
        }
        double range = effectiveRange(s, i, t);
        if (gap <= Math.max(0, range - MARGIN) + 1e-9) {
            ctx.go(UnitAi.ATTACKING);
            return;
        }
        MotionStore motion = s.motion();
        double dx = motion.x(t) - motion.x(i);
        double dz = motion.z(t) - motion.z(i);
        double distance = DetMath.length(dx, dz);
        double desired = Engagement.radius(s, i) + Engagement.radius(s, t) + Math.max(0, range - MARGIN);
        double px = motion.x(t) - dx / distance * desired;
        double pz = motion.z(t) - dz / distance * desired;
        double moved = DetMath.length(px - orders.targetX(i), pz - orders.targetZ(i));
        double away = DetMath.length(px - motion.x(i), pz - motion.z(i));
        if (moved > Math.max(REGOAL, away / 4)) {
            orders.setPlace(i, px, pz);
            s.bus().publish(new PlaceMoved(ctx.handle()));
        }
    }

    static void windUp(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        ctx.setData((int) s.tick() + s.stats().rangedPrepare(ctx.unit()), 1);
        int target = s.orders().target(ctx.unit());
        if (s.entities().isAlive(target)) Melee.face(s, ctx.unit(), ctx.handle(), Handle.index(target));
    }

    static void strike(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        int target = s.orders().target(i);
        if (!s.entities().isAlive(target)) {
            CombatActions.findNext(ctx);
            return;
        }
        int t = Handle.index(target);
        StatService stats = s.stats();
        if (s.orders().kind(i) == OrderKind.ENGAGE && Engagement.gap(s, i, t) > stats.vision(i)) {
            UnitAi.finishOrder(ctx, false);
            return;
        }
        double blocks = HighGround.advantage(s, i, t);
        double range = stats.rangedRange(i) * HighGround.rangeFactor(s, i, blocks);
        if (Engagement.gap(s, i, t) > range) {
            ctx.go(UnitAi.CHASING);
            return;
        }
        int tick = (int) s.tick();
        if (tick - ctx.data0() < 0) return; // modular int ticks keep cadence across the wrap
        Melee.face(s, i, ctx.handle(), t);
        double boost = HighGround.damageFactor(s, i, blocks);
        Damage damage = new Damage(
                target,
                ctx.handle(),
                (float) (stats.rangedDamage(i, 0) * boost),
                (float) (stats.rangedDamage(i, 1) * boost),
                (float) (stats.rangedDamage(i, 2) * boost),
                (float) (stats.rangedDamage(i, 3) * boost),
                List.of(),
                false);
        s.bus()
                .publish(new ShotRequested(
                        ctx.handle(), target, damage, range, stats.rangedSpeed(i), stats.rangedSpread(i)));
        ctx.setData(tick + stats.rangedRepeat(i), 1);
    }
}
