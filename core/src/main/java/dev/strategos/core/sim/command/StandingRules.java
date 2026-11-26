package dev.strategos.core.sim.command;

import dev.strategos.core.protocol.CommandKind;
import dev.strategos.core.protocol.CommandKinds;
import dev.strategos.core.protocol.Guard;
import dev.strategos.core.protocol.Patrol;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.order.Charges;
import dev.strategos.core.sim.order.OrderKind;
import java.util.ArrayList;
import java.util.List;

/**
 * The standing orders (ORD-07): patrol and guard. Each is joined to a unit's queue by the command's mode as a move is;
 * as neither ends by itself, an order queued after one waits until a new order replaces it.
 */
final class StandingRules {
    private StandingRules() {}

    /** Joins one order to unit {@code i}'s queue by {@code mode}; false when the queue is full. */
    private static boolean join(
            MatchState s,
            QueueMode mode,
            int i,
            int kind,
            double x,
            double z,
            int slot,
            int target,
            List<Vec2d> anchors) {
        return switch (mode) {
            case REPLACE -> {
                s.orders().replace(i, kind, x, z, 0, slot, target, anchors);
                yield true;
            }
            case QUEUE -> s.orders().queue(i, kind, x, z, 0, slot, target, anchors);
            case PUSH_FRONT -> s.orders().pushFront(i, kind, x, z, 0, slot, target, anchors);
        };
    }

    /**
     * Patrol: each owned unit loops through the command's points, the first of its anchors being its own place at the
     * time when the command names one point only. A command with no point, more than {@value Patrol#MAX_POINTS} or one
     * out of bounds is refused.
     */
    static final class PatrolRule implements CommandRule<Patrol> {
        @Override
        public CommandKind<Patrol> kind() {
            return CommandKinds.PATROL;
        }

        @Override
        public CommandResult apply(CommandEnvelope envelope, Patrol patrol, MatchState state) {
            int size = patrol.units().size();
            if (size == 0 || patrol.points().isEmpty() || patrol.points().size() > Patrol.MAX_POINTS) {
                return Tally.rejectAll(envelope, kind(), size, RejectReason.INVALID_ARGUMENT);
            }
            for (Vec2d point : patrol.points()) {
                if (!state.config().bounds().contains(point.x(), point.z())) {
                    return Tally.rejectAll(envelope, kind(), size, RejectReason.OUT_OF_BOUNDS);
                }
            }
            Tally tally = new Tally();
            for (int k = 0; k < size; k++) {
                int handle = patrol.units().get(k);
                RejectReason reason = Ownership.check(envelope.issuer(), handle, state);
                if (reason != null) {
                    tally.reject(reason);
                    continue;
                }
                int i = Handle.index(handle);
                List<Vec2d> anchors = patrol.points();
                int leg = 0;
                if (anchors.size() == 1) { // between where the unit stands and the point
                    anchors = new ArrayList<>(List.of(
                            new Vec2d(state.motion().x(i), state.motion().z(i)), anchors.get(0)));
                    leg = 1;
                }
                Vec2d first = anchors.get(leg);
                int before = state.orders().size(i);
                if (!join(state, patrol.mode(), i, OrderKind.PATROL, first.x(), first.z(), leg, 0, anchors)) {
                    tally.reject(RejectReason.QUEUE_FULL);
                    continue;
                }
                if (patrol.mode() != QueueMode.QUEUE || before == 0) { // a queued order waits, unless the unit is idle
                    state.bus().publish(new OrderReceived(handle));
                }
                tally.accept();
            }
            return tally.result(envelope, kind());
        }
    }

    /**
     * Guard: each owned unit follows the charge, a unit or building that is alive and the unit's own faction's or an
     * ally's. A charge that is not alive is refused as unknown, an enemy (or the unit itself) as an invalid argument.
     */
    static final class GuardRule implements CommandRule<Guard> {
        @Override
        public CommandKind<Guard> kind() {
            return CommandKinds.GUARD;
        }

        @Override
        public CommandResult apply(CommandEnvelope envelope, Guard guard, MatchState state) {
            int size = guard.units().size();
            int charge = guard.target();
            if (size == 0) {
                return Tally.rejectAll(envelope, kind(), 0, RejectReason.INVALID_ARGUMENT);
            }
            if (charge == Handle.NONE || !Charges.alive(state, charge)) {
                return Tally.rejectAll(envelope, kind(), size, RejectReason.UNKNOWN_TARGET);
            }
            int owner = Charges.factionOf(state, charge);
            double[] at = Charges.centre(state, charge);
            Tally tally = new Tally();
            for (int k = 0; k < size; k++) {
                int handle = guard.units().get(k);
                RejectReason reason = Ownership.check(envelope.issuer(), handle, state);
                if (reason != null) {
                    tally.reject(reason);
                    continue;
                }
                int i = Handle.index(handle);
                if (handle == charge || !state.allies().allied(state.identity().faction(i), owner)) {
                    tally.reject(RejectReason.INVALID_ARGUMENT);
                    continue;
                }
                int before = state.orders().size(i);
                if (!join(state, guard.mode(), i, OrderKind.GUARD, at[0], at[1], 0, charge, List.of())) {
                    tally.reject(RejectReason.QUEUE_FULL);
                    continue;
                }
                if (guard.mode() != QueueMode.QUEUE || before == 0) {
                    state.bus().publish(new OrderReceived(handle));
                }
                tally.accept();
            }
            return tally.result(envelope, kind());
        }
    }
}
