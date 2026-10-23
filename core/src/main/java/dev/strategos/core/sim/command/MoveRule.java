package dev.strategos.core.sim.command;

import dev.strategos.core.protocol.CommandKind;
import dev.strategos.core.protocol.CommandKinds;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.sim.order.SlotPlanner;

/**
 * Move (ORD-02, 04 §7.4): each owned unit gets a move order to the target, joined to its queue by the command's mode.
 * {@code REPLACE} makes it the only order, {@code QUEUE} appends it and {@code PUSH_FRONT} puts it before the current
 * one; a unit that holds {@value OrderQueues#MAX} orders refuses the last two with {@code QUEUE_FULL}. A command to
 * several units gives their orders one new group id, and each unit that takes it its own place round the target
 * ({@link SlotPlanner}, ORD-03). A unit whose current order changed hears {@link OrderReceived}, and its machine walks.
 */
final class MoveRule implements CommandRule<Move> {
    private final SlotPlanner slots = new SlotPlanner();

    @Override
    public CommandKind<Move> kind() {
        return CommandKinds.MOVE;
    }

    @Override
    public CommandResult apply(CommandEnvelope envelope, Move move, MatchState state) {
        return walk(
                envelope,
                kind(),
                OrderKind.MOVE,
                move.units(),
                move.target(),
                move.mode(),
                move.formation(),
                state,
                slots);
    }

    /**
     * Gives each owned unit of {@code units} an order of {@code orderKind} to {@code target}, joined by {@code mode},
     * each in its own place round it: a move's, and an attack-move's ({@link AttackMoveRule}).
     */
    static CommandResult walk(
            CommandEnvelope envelope,
            CommandKind<?> kind,
            int orderKind,
            UnitSet units,
            Vec2d target,
            QueueMode mode,
            int formation,
            MatchState state,
            SlotPlanner slots) {
        if (units.size() == 0) {
            return Tally.rejectAll(envelope, kind, 0, RejectReason.INVALID_ARGUMENT);
        }
        if (!state.config().bounds().contains(target.x(), target.z())) {
            return Tally.rejectAll(envelope, kind, units.size(), RejectReason.OUT_OF_BOUNDS);
        }
        if (formation != 0) {
            return Tally.rejectAll(envelope, kind, units.size(), RejectReason.UNSUPPORTED);
        }
        OrderQueues orders = state.orders();
        Tally tally = new Tally();
        int[] taking = new int[units.size()];
        int n = 0;
        for (int k = 0; k < units.size(); k++) {
            int handle = units.get(k);
            RejectReason reason = Ownership.check(envelope.issuer(), handle, state);
            if (reason != null) {
                tally.reject(reason);
            } else {
                taking[n++] = handle;
            }
        }
        int group = n > 0 && units.size() > 1 ? state.newGroup() : 0;
        int[] indices = new int[n];
        for (int k = 0; k < n; k++) {
            indices[k] = Handle.index(taking[k]);
        }
        boolean placed = n > 1;
        if (placed) {
            slots.plan(state, indices, target.x(), target.z());
        }
        for (int k = 0; k < n; k++) {
            int handle = taking[k];
            int i = indices[k];
            double x = placed ? slots.x(k) : target.x();
            double z = placed ? slots.z(k) : target.z();
            int slot = placed ? slots.slot(k) : 0;
            int before = orders.size(i);
            boolean joined = switch (mode) {
                case REPLACE -> {
                    orders.replace(i, orderKind, x, z, group, slot);
                    yield true;
                }
                case QUEUE -> orders.queue(i, orderKind, x, z, group, slot);
                case PUSH_FRONT -> orders.pushFront(i, orderKind, x, z, group, slot);
            };
            if (!joined) {
                tally.reject(RejectReason.QUEUE_FULL);
                continue;
            }
            if (mode != QueueMode.QUEUE || before == 0) { // a queued order waits, unless the unit is idle
                state.bus().publish(new OrderReceived(handle));
            }
            tally.accept();
        }
        return tally.result(envelope, kind);
    }
}
