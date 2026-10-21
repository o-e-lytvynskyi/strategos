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

/** Move: each owned unit gets a move order to the target; the unit's AI starts walking from the bus. */
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
        if (move.mode() != QueueMode.REPLACE || move.formation() != 0) {
            return Tally.rejectAll(envelope, kind(), units.size(), RejectReason.UNSUPPORTED);
        }
        Tally tally = new Tally();
        for (int i = 0; i < units.size(); i++) {
            int handle = units.get(i);
            RejectReason reason = Ownership.check(envelope.issuer(), handle, state);
            if (reason != null) {
                tally.reject(reason);
                continue;
            }
            state.orders()
                    .setMove(
                            Handle.index(handle),
                            move.target().x(),
                            move.target().z());
            state.bus().publish(new OrderReceived(handle));
            tally.accept();
        }
        return tally.result(envelope, kind());
    }
}
