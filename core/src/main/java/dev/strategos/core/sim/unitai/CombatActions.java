package dev.strategos.core.sim.unitai;

import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.Stance;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.OrderFinished;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.PlaceMoved;
import dev.strategos.core.sim.combat.Engagement;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.spatial.UnitFilter;
import dev.strategos.core.sim.unitai.Hfsm.Context;
import java.util.List;

/** Dispatches the shared combat states to the unit's preferred available weapon. */
final class CombatActions {
    private CombatActions() {}

    /**
     * The enemy unit {@code ctx} takes up on its own, or -1: among those its stance lets it notice, the first of its
     * attack's preferred classes that any belongs to, then the nearest, the lower index on a tie (CMB-05).
     */
    static int nearestEnemy(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        if (!s.stats().canAttack(i)) return -1;
        Stance stance = Stances.of(s, i);
        if (stance == Stance.PASSIVE) return -1;
        MotionStore motion = s.motion();
        double x = motion.x(i);
        double z = motion.z(i);
        double radius = Stances.radius(s, i, stance);
        double qx = x;
        double qz = z;
        double qr = radius;
        if (stance == Stance.DEFENSIVE) {
            qx = Stances.anchorX(s, i);
            qz = Stances.anchorZ(s, i);
        } else if (stance == Stance.STAND_GROUND) {
            qr += Stances.searchSlack(s, i);
        }
        boolean stand = stance == Stance.STAND_GROUND;
        List<String> preferred = s.stats().preferredClasses(i, s.stats().canRanged(i));
        double[] best = {Double.POSITIVE_INFINITY};
        int[] rank = {preferred.size() + 1};
        int[] found = {-1};
        s.spatial()
                .forEachInRadius(
                        qx,
                        qz,
                        qr,
                        UnitFilter.enemiesOf(s.identity().faction(i), s.config().factions()),
                        j -> {
                            if (!s.entities().aliveAt(j) || s.garrisoned(j))
                                return; // the grid may still hold a unit killed or garrisoned since its rebuild
                            if (stand && Engagement.gap(s, i, j) > radius + 1e-9) return;
                            double d = DetMath.length(motion.x(j) - x, motion.z(j) - z);
                            int r = preferred.isEmpty() ? 0 : rankOf(s, j, preferred);
                            if (r < rank[0] || (r == rank[0] && d < best[0])) { // ascending indices: the lower on a tie
                                rank[0] = r;
                                best[0] = d;
                                found[0] = j;
                            }
                        });
        return found[0];
    }

    /** The place in {@code preferred} of the first class unit {@code j} has, or one past the end for none. */
    private static int rankOf(MatchState s, int j, List<String> preferred) {
        List<String> classes = s.stats().classes(j);
        for (int k = 0; k < preferred.size(); k++) {
            if (classes.contains(preferred.get(k))) return k;
        }
        return preferred.size();
    }

    /**
     * Whether unit {@code ctx}'s pursuit of its target {@code t} is over: an engagement it took up itself, or any
     * chase, ends when the target leaves its sight (0 A.D.'s rule, even under a player's order); a defensive unit's
     * engagement also ends when the target is beyond its radius from its spot.
     */
    static boolean pursuitOver(Context<MatchState> ctx, int t, boolean chasing) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        boolean engagement = s.orders().kind(i) == OrderKind.ENGAGE;
        if ((engagement || chasing) && Engagement.gap(s, i, t) > s.stats().vision(i)) {
            return true;
        }
        if (engagement && Stances.of(s, i) == Stance.DEFENSIVE && s.stances().hasSpot(i)) {
            double d = DetMath.length(
                    s.motion().x(t) - s.stances().spotX(i),
                    s.motion().z(t) - s.stances().spotZ(i));
            return d > Stances.radius(s, i, Stance.DEFENSIVE);
        }
        return false;
    }

    /** Whether the unit stands its ground in an engagement of its own: it fights from where it is, or not at all. */
    static boolean holding(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        return s.orders().kind(ctx.unit()) == OrderKind.ENGAGE && Stances.of(s, ctx.unit()) == Stance.STAND_GROUND;
    }

    /** A stand-ground unit's engagement with a target out of its reach is dropped; true when it was. */
    private static boolean dropped(Context<MatchState> ctx) {
        if (!holding(ctx)) return false;
        MatchState s = ctx.host();
        int target = s.orders().target(ctx.unit());
        if (!s.entities().isAlive(target)
                || Engagement.gap(s, ctx.unit(), Handle.index(target)) <= Stances.reach(s, ctx.unit()) + 1e-9) {
            return false;
        }
        UnitAi.finishOrder(ctx, false);
        return true;
    }

    static boolean engage(Context<MatchState> ctx, int target) {
        MatchState s = ctx.host();
        Stances.engaging(s, ctx.unit());
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

    /** Hit by {@code attacker} (a handle): a passive unit runs, the others hit back if their stance lets them. */
    static void retaliate(Context<MatchState> ctx, int attacker) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        if (!s.entities().isAlive(attacker)) return;
        Stance stance = Stances.of(s, i);
        if (stance == Stance.PASSIVE) {
            Fleeing.away(ctx, attacker, Stances.fleeDistance(s, i));
            return;
        }
        if (!s.stats().canAttack(i)) return;
        int a = Handle.index(attacker);
        boolean near = switch (stance) {
            case STAND_GROUND -> Engagement.gap(s, i, a) <= Stances.reach(s, i) + 1e-9;
            case DEFENSIVE ->
                DetMath.length(
                                s.motion().x(a) - Stances.anchorX(s, i),
                                s.motion().z(a) - Stances.anchorZ(s, i))
                        <= Stances.radius(s, i, stance);
            default -> true;
        };
        if (near) engage(ctx, a);
    }

    static void findNext(Context<MatchState> ctx) {
        if (spreadNext(ctx)) return;
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

    /**
     * A spread attacker (ORD-05) whose target is gone takes the nearest target of the others on its spread, keeping
     * the group; true when it did.
     */
    private static boolean spreadNext(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        int group = s.orders().group(i);
        if (group == 0 || s.orders().kind(i) != OrderKind.ATTACK) return false;
        int next = Spreading.repick(s, i, group);
        if (next == 0) return false;
        int t = Handle.index(next);
        s.orders().pop(i);
        s.bus().publish(new OrderFinished(ctx.handle(), false));
        s.orders().pushFront(i, OrderKind.ATTACK, s.motion().x(t), s.motion().z(t), group, 0, next);
        s.bus().publish(new PlaceMoved(ctx.handle()));
        ctx.go(UnitAi.APPROACHING);
        return true;
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
        if (dropped(ctx)) return;
        if (ranged(ctx)) Ranged.takePlace(ctx);
        else Melee.takePlace(ctx, engagement);
    }

    static void close(Context<MatchState> ctx, boolean chasing, Engagement engagement) {
        if (lostRangedWeapon(ctx)) {
            UnitAi.finishOrder(ctx, false);
            return;
        }
        if (dropped(ctx)) return;
        int slot = ctx.host().orders().slot(ctx.unit());
        if (ranged(ctx)) {
            if (slot >= 0) Ranged.takePlace(ctx);
            else Ranged.close(ctx, chasing);
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
        if (dropped(ctx)) return;
        int slot = ctx.host().orders().slot(ctx.unit());
        if (ranged(ctx)) {
            if (slot >= 0) Ranged.takePlace(ctx);
            else Ranged.close(ctx, false);
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
