package dev.strategos.core.sim.unitai;

import dev.strategos.core.content.Footprint;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.PlaceMoved;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.order.Charges;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.sim.unitai.Hfsm.Context;

/**
 * What the guard states do (ORD-07): stay within {@value #CLOSE} blocks of the charge, a friendly unit or building the
 * order names, walking (GUARDING) to a place that distance from it, on the side the guard is on, once it is more than
 * {@value #SLACK} blocks further, and standing (GUARD_STANDING, which reports IDLE) beside it. Every think an enemy within the stance's radius of the charge is engaged (the engagement goes
 * before the order, so the guard comes back to it); the order is over, done, when the charge is gone or no longer a
 * friend's. An engagement that draws the guard more than {@value #LEASH} blocks beyond that radius from the charge is
 * given up ({@link #beyondLeash}).
 */
final class Guarding {
    /** How close a guard stands to a unit it guards, in blocks; to a building, from its footprint's edge. */
    static final double CLOSE = 3.0;
    /** How much further than {@value #CLOSE} the guard may be before it walks back. */
    static final double SLACK = 2.0;
    /** How far past its stance's radius from the charge an engagement may take a guard. */
    static final double LEASH = 4.0;

    private Guarding() {}

    /** How far from the charge's centre the guard stands: {@value #CLOSE}, and a building's half-span besides. */
    private static double standOff(MatchState s, int charge) {
        if (!BuildingStore.isBuilding(charge)) {
            return CLOSE;
        }
        Footprint at = s.footprintOf(charge);
        return CLOSE + Math.max(at.spanX(), at.spanZ()) / 2.0;
    }

    /** Whether the charge is alive and still a friend of the guard {@code i}'s faction. */
    private static boolean kept(MatchState s, int i, int charge) {
        return Charges.alive(s, charge) && s.allies().allied(s.identity().faction(i), Charges.factionOf(s, charge));
    }

    /**
     * GUARDING's enter: the place beside the charge on the guard's side, or, if the guard is within {@value #SLACK}
     * blocks of {@link #standOff} already, it stands where it is.
     */
    static void enter(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        int charge = s.orders().target(i);
        if (over(ctx, charge)) {
            return;
        }
        double[] c = Charges.centre(s, charge);
        double dx = s.motion().x(i) - c[0];
        double dz = s.motion().z(i) - c[1];
        double d = DetMath.length(dx, dz);
        double stand = standOff(s, charge);
        if (d <= stand + SLACK) {
            halt(ctx);
            return;
        }
        s.orders().setPlace(i, c[0] + dx / d * stand, c[1] + dz / d * stand);
        s.bus().publish(new PlaceMoved(ctx.handle()));
    }

    /**
     * GUARDING's think: the charge lost or turned ends the order; an enemy near it is engaged; within {@value #SLACK}
     * blocks of it the guard stops; and the place follows the charge once that has moved {@value Following#REGOAL}
     * blocks from it.
     */
    static void walk(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        int charge = s.orders().target(i);
        if (over(ctx, charge) || watch(ctx, charge)) {
            return;
        }
        double[] c = Charges.centre(s, charge);
        double dx = s.motion().x(i) - c[0];
        double dz = s.motion().z(i) - c[1];
        double d = DetMath.length(dx, dz);
        double stand = standOff(s, charge);
        if (d <= stand + SLACK) {
            halt(ctx);
            return;
        }
        double px = c[0] + dx / d * stand;
        double pz = c[1] + dz / d * stand;
        if (DetMath.length(px - s.orders().targetX(i), pz - s.orders().targetZ(i)) > Following.REGOAL) {
            s.orders().setPlace(i, px, pz);
            s.bus().publish(new PlaceMoved(ctx.handle()));
        }
    }

    /** GUARD_STANDING's think: as {@link #walk}'s, and a charge that has moved on more than {@value #SLACK} blocks is followed. */
    static void stand(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        int charge = s.orders().target(i);
        if (over(ctx, charge) || watch(ctx, charge)) {
            return;
        }
        double[] c = Charges.centre(s, charge);
        if (DetMath.length(s.motion().x(i) - c[0], s.motion().z(i) - c[1]) > standOff(s, charge) + SLACK) {
            ctx.go(UnitAi.GUARDING);
        }
    }

    /** Whether the order is over: the charge is gone or no longer a friend's, and the order is done. */
    private static boolean over(Context<MatchState> ctx, int charge) {
        if (kept(ctx.host(), ctx.unit(), charge)) {
            return false;
        }
        UnitAi.finishOrder(ctx, false);
        return true;
    }

    /**
     * The part of a think the two states share when the charge is kept: true when it is over for this think, the
     * charge being out of reach inside a building or an enemy near it taken on.
     */
    private static boolean watch(Context<MatchState> ctx, int charge) {
        MatchState s = ctx.host();
        if (!BuildingStore.isBuilding(charge) && s.garrisoned(Handle.index(charge))) {
            return true; // inside a building: nothing to follow or defend
        }
        double[] c = Charges.centre(s, charge);
        int enemy = CombatActions.nearestEnemyNear(ctx, c[0], c[1]);
        return enemy >= 0 && CombatActions.engage(ctx, enemy);
    }

    /** The guard stays where it stands: its place is that, it arrives there at once, and it is GUARD_STANDING. */
    static void halt(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        s.orders().setPlace(i, s.motion().x(i), s.motion().z(i));
        s.bus().publish(new PlaceMoved(ctx.handle()));
        ctx.go(UnitAi.GUARD_STANDING);
    }

    /**
     * Whether unit {@code i}, engaging before a guard order, has been drawn more than {@value #LEASH} blocks past its
     * stance's radius from the charge.
     */
    static boolean beyondLeash(MatchState s, int i) {
        OrderQueues orders = s.orders();
        if (orders.size(i) < 2) {
            return false;
        }
        OrderQueues.Order next = orders.order(i, 1);
        if (next.kind() != OrderKind.GUARD || !Charges.alive(s, next.target())) {
            return false;
        }
        double[] c = Charges.centre(s, next.target());
        double d = DetMath.length(s.motion().x(i) - c[0], s.motion().z(i) - c[1]);
        return d > Stances.radius(s, i, Stances.of(s, i)) + LEASH;
    }
}
