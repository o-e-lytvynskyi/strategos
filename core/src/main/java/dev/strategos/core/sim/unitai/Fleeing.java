package dev.strategos.core.sim.unitai;

import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.MatchBounds;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.unitai.Hfsm.Context;

/**
 * Running from a blow (UNIT-04): a unit that cannot hit back and has a flee distance takes up a {@link OrderKind#FLEE}
 * before its orders, to the point that far from it straight away from its attacker, kept half a block inside the match
 * space; the movement systems walk it as a move, and once it is there the unit goes back to its next order.
 */
final class Fleeing {
    private static final double INSIDE = 0.5;

    private Fleeing() {}

    /** Unit {@code ctx} runs from {@code attacker}, a handle, if it flees and the attacker lives; else nothing. */
    static void away(Context<MatchState> ctx, int attacker) {
        away(ctx, attacker, ctx.host().stats().fleeDistance(ctx.unit()));
    }

    /** Unit {@code ctx} runs {@code distance} blocks from {@code attacker} if that is above 0 and the attacker lives. */
    static void away(Context<MatchState> ctx, int attacker, double distance) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        if (distance <= 0 || !s.entities().isAlive(attacker)) {
            return;
        }
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
        double x = clamp(m.x(i) + dx / length * distance, b.sizeX());
        double z = clamp(m.z(i) + dz / length * distance, b.sizeZ());
        if (s.orders().pushFront(i, OrderKind.FLEE, x, z, 0, 0, 0)) {
            s.bus().publish(new OrderReceived(ctx.handle())); // it walks, and asks for a path
        }
    }

    private static double clamp(double v, int size) {
        return Math.max(INSIDE, Math.min(size - INSIDE, v));
    }
}
