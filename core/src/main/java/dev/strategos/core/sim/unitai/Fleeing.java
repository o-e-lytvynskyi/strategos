package dev.strategos.core.sim.unitai;

import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.Stance;
import dev.strategos.core.sim.MatchBounds;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.PlaceMoved;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.unitai.Hfsm.Context;

/**
 * Running from a blow (UNIT-04, CMB-05): a unit that cannot hit back and has a flee distance, or a passive one, takes up
 * a {@link OrderKind#FLEE} before its orders, which holds its attacker as its target and is carried out in {@link
 * UnitAi#FLEEING}. The point to walk to is always that far from the attacker's current place straight away from it,
 * kept half a block inside the match space, and is planned again each think; the unit goes back to its next order once
 * it is that far from the attacker, or the attacker is dead or gone. A blow from another attacker turns the flight.
 */
final class Fleeing {
    private static final double INSIDE = 0.5;
    /** How far the planned point must move to be walked to afresh, in blocks. */
    private static final double REGOAL = 1.0;

    private Fleeing() {}

    /** Unit {@code ctx} runs from {@code attacker}, a handle, if it flees and the attacker lives; else nothing. */
    static void away(Context<MatchState> ctx, int attacker) {
        away(ctx, attacker, distance(ctx.host(), ctx.unit()));
    }

    /** Unit {@code ctx} runs {@code distance} blocks from {@code attacker} if that is above 0 and the attacker lives. */
    static void away(Context<MatchState> ctx, int attacker, double distance) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        if (distance <= 0 || !s.entities().isAlive(attacker)) {
            return;
        }
        if (ctx.in(UnitAi.FLEEING) && s.orders().kind(i) == OrderKind.FLEE) { // another blow: turn the flight
            s.orders().retarget(i, attacker, 0);
            plan(ctx, attacker, distance, true);
            return;
        }
        double[] at = point(s, i, attacker, distance);
        if (s.orders().pushFront(i, OrderKind.FLEE, at[0], at[1], 0, 0, attacker)) {
            s.bus().publish(new OrderReceived(ctx.handle())); // it walks, and asks for a path
        }
    }

    /** How far unit {@code i} runs from a blow: a passive unit's, else its template's flee distance, 0 for none. */
    static double distance(MatchState s, int i) {
        return Stances.of(s, i) == Stance.PASSIVE
                ? Stances.fleeDistance(s, i)
                : s.stats().fleeDistance(i);
    }

    /** FLEEING's think: over once far enough from a living attacker, else the point is planned again. */
    static void think(Context<MatchState> ctx) {
        step(ctx, false);
    }

    /** FLEEING's arrival: over if far enough or no farther place exists, else on from here. */
    static void arrived(Context<MatchState> ctx) {
        step(ctx, true);
    }

    private static void step(Context<MatchState> ctx, boolean arrived) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        if (s.orders().kind(i) != OrderKind.FLEE) {
            return;
        }
        int attacker = s.orders().target(i);
        double distance = distance(s, i);
        if (distance <= 0 || !s.entities().isAlive(attacker) || apart(s, i, attacker) >= distance) {
            UnitAi.finishOrder(ctx, false);
            return;
        }
        if (!plan(ctx, attacker, distance, arrived) && arrived) {
            UnitAi.finishOrder(ctx, false); // nowhere farther to go: a corner of the match space
        }
    }

    /**
     * Plans the point again; true when the unit has somewhere to go. The new point is walked to when it differs from
     * the current one by more than {@value #REGOAL} block, or when {@code force}d (the attacker changed).
     */
    private static boolean plan(Context<MatchState> ctx, int attacker, double distance, boolean force) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        double[] at = point(s, i, attacker, distance);
        if (DetMath.length(at[0] - s.motion().x(i), at[1] - s.motion().z(i)) < REGOAL) {
            return false;
        }
        if (force
                || DetMath.length(
                                at[0] - s.orders().targetX(i),
                                at[1] - s.orders().targetZ(i))
                        > REGOAL) {
            s.orders().setPlace(i, at[0], at[1]);
            s.bus().publish(new PlaceMoved(ctx.handle()));
        }
        return true;
    }

    private static double apart(MatchState s, int i, int attacker) {
        MotionStore m = s.motion();
        int a = Handle.index(attacker);
        return DetMath.length(m.x(i) - m.x(a), m.z(i) - m.z(a));
    }

    /** The point {@code distance} blocks from {@code attacker} on the line from it through unit {@code i}. */
    private static double[] point(MatchState s, int i, int attacker, double distance) {
        MotionStore m = s.motion();
        int a = Handle.index(attacker);
        double dx = m.x(i) - m.x(a);
        double dz = m.z(i) - m.z(a);
        double length = DetMath.length(dx, dz);
        if (length < 1e-6) {
            dx = 1; // on top of each other: east, the same way every run
            dz = 0;
            length = 1;
        }
        MatchBounds b = s.config().bounds();
        return new double[] {
            clamp(m.x(a) + dx / length * distance, b.sizeX()), clamp(m.z(a) + dz / length * distance, b.sizeZ())
        };
    }

    private static double clamp(double v, int size) {
        return Math.max(INSIDE, Math.min(size - INSIDE, v));
    }
}
