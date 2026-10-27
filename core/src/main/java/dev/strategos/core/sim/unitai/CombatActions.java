package dev.strategos.core.sim.unitai;

import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.OrderFinished;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.PlaceMoved;
import dev.strategos.core.sim.combat.Engagement;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.spatial.UnitFilter;
import dev.strategos.core.sim.unitai.Hfsm.Context;

/** Dispatches the shared combat states to the unit's preferred available weapon. */
final class CombatActions {
    private CombatActions() {}

    static int nearestEnemy(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        if (!s.stats().canAttack(i)) return -1;
        MotionStore motion = s.motion();
        double x = motion.x(i);
        double z = motion.z(i);
        double[] best = {Double.POSITIVE_INFINITY};
        int[] found = {-1};
        s.spatial()
                .forEachInRadius(
                        x,
                        z,
                        s.stats().vision(i),
                        UnitFilter.enemiesOf(s.identity().faction(i), s.config().factions()),
                        j -> {
                            if (s.entities().aliveAt(j)) { // the grid may still contain a unit killed since its rebuild
                                double d = DetMath.length(motion.x(j) - x, motion.z(j) - z);
                                if (d < best[0]) { // ascending indices: the lower on a tie
                                    best[0] = d;
                                    found[0] = j;
                                }
                            }
                        });
        return found[0];
    }

    static boolean engage(Context<MatchState> ctx, int target) {
        MatchState s = ctx.host();
        int handle = s.entities().handleAt(target);
        if (!s.orders()
                .pushFront(
                        ctx.unit(),
                        OrderKind.ENGAGE,
                        s.motion().x(target),
                        s.motion().z(target),
                        0,
                        0,
                        handle)) {
            return false;
        }
        s.bus().publish(new OrderReceived(ctx.handle()));
        return true;
    }

    static void retaliate(Context<MatchState> ctx, int attacker) {
        MatchState s = ctx.host();
        if (s.stats().canAttack(ctx.unit()) && s.entities().isAlive(attacker)) {
            engage(ctx, Handle.index(attacker));
        }
    }

    static void findNext(Context<MatchState> ctx) {
        int next = nearestEnemy(ctx);
        if (next < 0) {
            UnitAi.finishOrder(ctx, false);
            return;
        }
        MatchState s = ctx.host();
        int i = ctx.unit();
        s.orders().pop(i);
        s.bus().publish(new OrderFinished(ctx.handle(), false));
        s.orders()
                .pushFront(
                        i,
                        OrderKind.ENGAGE,
                        s.motion().x(next),
                        s.motion().z(next),
                        0,
                        0,
                        s.entities().handleAt(next));
        s.bus().publish(new PlaceMoved(ctx.handle()));
        ctx.go(UnitAi.APPROACHING);
    }

    private static boolean ranged(Context<MatchState> ctx) {
        return ctx.host().stats().canRanged(ctx.unit());
    }

    /** A ranged order disabled by modifiers must end; older synthetic unarmed melee states keep their old path. */
    private static boolean lostRangedWeapon(Context<MatchState> ctx) {
        return (ctx.data1() == 1 || ctx.host().orders().slot(ctx.unit()) < 0)
                && !ctx.host().stats().canAttack(ctx.unit());
    }

    static void takePlace(Context<MatchState> ctx, Engagement engagement) {
        if (lostRangedWeapon(ctx)) {
            UnitAi.finishOrder(ctx, false);
            return;
        }
        if (ranged(ctx)) Ranged.takePlace(ctx);
        else Melee.takePlace(ctx, engagement);
    }

    static void close(Context<MatchState> ctx, boolean chasing, Engagement engagement) {
        if (lostRangedWeapon(ctx)) {
            UnitAi.finishOrder(ctx, false);
            return;
        }
        int slot = ctx.host().orders().slot(ctx.unit());
        if (ranged(ctx)) {
            if (slot >= 0) Ranged.takePlace(ctx);
            else Ranged.close(ctx);
        } else if (slot < 0) {
            Melee.takePlace(ctx, engagement);
        } else {
            Melee.close(ctx, chasing, engagement);
        }
    }

    static void arrived(Context<MatchState> ctx, Engagement engagement) {
        if (lostRangedWeapon(ctx)) {
            UnitAi.finishOrder(ctx, false);
            return;
        }
        int slot = ctx.host().orders().slot(ctx.unit());
        if (ranged(ctx)) {
            if (slot >= 0) Ranged.takePlace(ctx);
            else Ranged.close(ctx);
        } else if (slot < 0) {
            Melee.takePlace(ctx, engagement);
        } else {
            Melee.arrived(ctx, engagement);
        }
    }

    static void windUp(Context<MatchState> ctx) {
        if (lostRangedWeapon(ctx)) {
            UnitAi.finishOrder(ctx, false);
            return;
        }
        if (ranged(ctx)) Ranged.windUp(ctx);
        else Melee.windUp(ctx);
    }

    static void strike(Context<MatchState> ctx) {
        if (lostRangedWeapon(ctx)) {
            UnitAi.finishOrder(ctx, false);
            return;
        }
        boolean ranged = ranged(ctx);
        if ((ranged ? 1 : 0) != ctx.data1()) {
            ctx.go(UnitAi.APPROACHING); // retake the right place and start that weapon's own wind-up
        } else if (ranged) {
            Ranged.strike(ctx);
        } else {
            Melee.strike(ctx);
        }
    }
}
