package dev.strategos.core.sim.command;

import dev.strategos.core.protocol.CommandKind;
import dev.strategos.core.protocol.CommandKinds;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.OrderQueues;

/**
 * Move (ORD-02, 04 §7.4): each owned unit gets a move order to the target, joined to its queue by the command's mode.
 * {@code REPLACE} makes it the only order, {@code QUEUE} appends it and {@code PUSH_FRONT} puts it before the current
 * one; a unit that holds {@value OrderQueues#MAX} orders refuses the last two with {@code QUEUE_FULL}. A command to
 * several units gives their orders one new group id. A unit whose current order changed hears {@link OrderReceived},
 * and its machine walks.
 */
final class MoveRule implements CommandRule<Move> {
    @Override
    public CommandKind<Move> kind() {
        return CommandKinds.MOVE;
    }

    @Override
    public CommandResult apply(CommandEnvelope envelope, Move move, MatchState state) {
        UnitSet units = move.units();
        if (units.size() == 0) {
            return Tally.rejectAll(envelope, kind(), 0, RejectReason.INVALID_ARGUMENT);
        }
        if (!state.config().bounds().contains(move.target().x(), move.target().z())) {
            return Tally.rejectAll(envelope, kind(), units.size(), RejectReason.OUT_OF_BOUNDS);
        }
        if (move.formation() != 0) {
            return Tally.rejectAll(envelope, kind(), units.size(), RejectReason.UNSUPPORTED);
        }
        OrderQueues orders = state.orders();
        double x = move.target().x();
        double z = move.target().z();
        int group = 0;
        Tally tally = new Tally();
        for (int k = 0; k < units.size(); k++) {
            int handle = units.get(k);
            RejectReason reason = Ownership.check(envelope.issuer(), handle, state);
            if (reason != null) {
                tally.reject(reason);
                continue;
            }
            if (group == 0 && units.size() > 1) {
                group = state.newGroup(); // only once some unit takes the order
            }
            int i = Handle.index(handle);
            int before = orders.size(i);
            boolean joined = switch (move.mode()) {
                case REPLACE -> {
                    orders.replace(i, OrderKind.MOVE, x, z, group, 0);
                    yield true;
                }
                case QUEUE -> orders.queue(i, OrderKind.MOVE, x, z, group, 0);
                case PUSH_FRONT -> orders.pushFront(i, OrderKind.MOVE, x, z, group, 0);
            };
            if (!joined) {
                tally.reject(RejectReason.QUEUE_FULL);
                continue;
            }
            if (move.mode() != QueueMode.QUEUE || before == 0) { // a queued order waits, unless the unit is idle
                state.bus().publish(new OrderReceived(handle));
            }
            tally.accept();
        }
        return tally.result(envelope, kind());
    }
}
