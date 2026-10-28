package dev.strategos.core.sim.unitai;

import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.PlaceMoved;
import dev.strategos.core.sim.UnitStruck;
import dev.strategos.core.sim.combat.Damage;
import dev.strategos.core.sim.combat.Engagement;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.sim.stats.StatService;
import dev.strategos.core.sim.unitai.Hfsm.Context;
import java.util.ArrayList;
import java.util.List;

/**
 * What the COMBAT states and the aggressive behaviour do (CMB-02, 04 §6.7): take up an engagement, walk to a place
 * round the target, strike on the wind-up and then every period, chase a target that steps out of reach, and find the
 * next one. An engagement the unit took up itself ({@link OrderKind#ENGAGE}) ends when its target leaves the unit's
 * sight; the player's attack ({@link OrderKind#ATTACK}) when the target dies or the order is replaced, or when the target
 * leaves its sight while the unit chases it.
 */
final class Melee {
    /** How far past its reach a target may step before the attacker chases it, so a shuffle does not break a fight. */
    static final double LEAVE = 0.5;
    /** The least a place must move before the attacker walks to it afresh, in blocks. */
    static final double REGOAL = 1.0;

    private Melee() {}

    /**
     * Entering APPROACHING: the unit takes a place round its target, unless the target is gone, and strikes at once if
     * the target already stands in reach.
     */
    static void takePlace(Context<MatchState> ctx, Engagement engagement) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        int target = s.orders().target(i);
        if (Engagement.gone(s, target)) {
            return; // its think finds the next
        }
        s.orders().retarget(i, target, engagement.choose(s, i, target));
        if (inReach(s, i, Handle.index(target))) {
            ctx.go(UnitAi.ATTACKING);
        }
    }

    /**
     * APPROACHING's and CHASING's think, and their answer to an arrival: strike once in reach, give up a target out of
     * sight unless the player ordered it, else walk on to the place round the target (or, chasing, the target itself).
     */
    static void close(Context<MatchState> ctx, boolean chasing, Engagement engagement) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        OrderQueues orders = s.orders();
        int target = orders.target(i);
        if (Engagement.gone(s, target)) {
            CombatActions.findNext(ctx);
            return;
        }
        int t = Handle.index(target);
        if (inReach(s, i, t)) {
            ctx.go(UnitAi.ATTACKING);
            return;
        }
        if (CombatActions.pursuitOver(ctx, t, chasing)) {
            UnitAi.finishOrder(ctx, false);
            return;
        }
        MotionStore motion = s.motion();
        double body = Engagement.radius(s, i);
        double radii = body + Engagement.radius(s, t);
        double px = motion.x(t) + (chasing ? 0 : Engagement.offsetX(orders.slot(i), radii, body));
        double pz = motion.z(t) + (chasing ? 0 : Engagement.offsetZ(orders.slot(i), radii, body));
        double moved = DetMath.length(px - orders.targetX(i), pz - orders.targetZ(i));
        double away = DetMath.length(px - motion.x(i), pz - motion.z(i));
        if (moved > Math.max(REGOAL, away / 4)) {
            orders.setPlace(i, px, pz);
            s.bus().publish(new PlaceMoved(ctx.handle()));
        }
    }

    /** An arrival short of reach: the place was taken or the target moved; the unit takes the nearest free one. */
    static void arrived(Context<MatchState> ctx, Engagement engagement) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        int target = s.orders().target(i);
        if (s.entities().isAlive(target)
                && Engagement.gap(s, i, Handle.index(target)) > s.stats().meleeReach(i)) {
            s.orders().retarget(i, target, engagement.choose(s, i, target));
        }
        close(ctx, false, engagement);
    }

    /**
     * Entering ATTACKING: the wind-up starts; the first data word is the tick of the first hit, the wind-up from now.
     * The unit faces its target.
     */
    static void windUp(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        ctx.setData((int) s.tick() + s.stats().meleePrepare(ctx.unit()), 0);
        face(s, ctx.unit(), ctx.handle(), Handle.index(s.orders().target(ctx.unit())));
    }

    /**
     * ATTACKING's think, every tick: a target gone is replaced, one out of reach chased; otherwise, on the tick in the
     * first data word, the unit strikes, and the next hit is a period later.
     */
    static void strike(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        int target = s.orders().target(i);
        if (Engagement.gone(s, target)) {
            CombatActions.findNext(ctx);
            return;
        }
        int t = Handle.index(target);
        StatService stats = s.stats();
        if (Engagement.gap(s, i, t) > stats.meleeReach(i) + LEAVE) {
            if (CombatActions.holding(ctx)) {
                UnitAi.finishOrder(ctx, false); // standing its ground, it lets the target go
            } else {
                ctx.go(UnitAi.CHASING);
            }
            return;
        }
        int tick = (int) s.tick();
        if (tick < ctx.data0()) {
            return;
        }
        face(s, i, ctx.handle(), t);
        s.queueDamage(new Damage(
                target,
                ctx.handle(),
                (float) stats.meleeDamage(i, 0),
                (float) stats.meleeDamage(i, 1),
                (float) stats.meleeDamage(i, 2),
                0,
                bonuses(stats, i),
                false));
        s.outputs().add(new UnitStruck(ctx.handle(), target));
        ctx.setData(tick + stats.meleeRepeat(i), 0);
    }

    /** The multipliers unit {@code i}'s blows carry against classes (UNIT-07); none for most units. */
    private static List<Damage.Bonus> bonuses(StatService stats, int i) {
        int count = stats.meleeBonusCount();
        if (count == 0) {
            return List.of();
        }
        List<Damage.Bonus> out = null;
        for (int k = 0; k < count; k++) {
            double multiplier = stats.meleeBonus(i, k);
            if (multiplier > 0) {
                if (out == null) {
                    out = new ArrayList<>(2);
                }
                out.add(new Damage.Bonus(stats.meleeBonusToken(k), (float) multiplier));
            }
        }
        return out == null ? List.of() : out;
    }

    /** Whether unit {@code t} stands within unit {@code i}'s reach, give or take rounding. */
    static boolean inReach(MatchState s, int i, int t) {
        return Engagement.gap(s, i, t) <= s.stats().meleeReach(i) + 1e-9;
    }

    static void face(MatchState s, int i, int handle, int t) {
        MotionStore motion = s.motion();
        double dx = motion.x(t) - motion.x(i);
        double dz = motion.z(t) - motion.z(i);
        if (dx == 0 && dz == 0) {
            return;
        }
        float yaw = DetMath.yawDegrees(dx, dz);
        if (yaw != motion.yaw(i)) {
            motion.set(i, motion.x(i), motion.y(i), motion.z(i), yaw);
            s.frame().add(handle, motion.x(i), motion.y(i), motion.z(i), yaw, (byte) 0);
        }
    }
}
