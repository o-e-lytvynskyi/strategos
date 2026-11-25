package dev.strategos.core.sim.command;

import dev.strategos.core.protocol.CommandKind;
import dev.strategos.core.protocol.CommandKinds;
import dev.strategos.core.protocol.FormationMove;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.formation.FormationController;
import dev.strategos.core.sim.formation.Formations;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.SlotPlanner;
import java.util.Arrays;

/**
 * Formation move (ORD-06, 04 §9.4): the owned units named make one formation that marches to the target, each unit's
 * order a {@link OrderKind#FORMATION} naming it. Only {@code REPLACE} is taken, as the formation is a standing thing and
 * has no place in a queue. A unit inside a building is refused; a command that leaves one unit to march is a plain
 * move, a formation of one being nothing. A target no route leads to is refused as an invalid argument.
 */
final class FormationMoveRule implements CommandRule<FormationMove> {
    private final SlotPlanner slots = new SlotPlanner();

    @Override
    public CommandKind<FormationMove> kind() {
        return CommandKinds.FORMATION_MOVE;
    }

    @Override
    public CommandResult apply(CommandEnvelope envelope, FormationMove move, MatchState state) {
        int size = move.units().size();
        if (size == 0) {
            return Tally.rejectAll(envelope, kind(), 0, RejectReason.INVALID_ARGUMENT);
        }
        if (!state.config().bounds().contains(move.target().x(), move.target().z())) {
            return Tally.rejectAll(envelope, kind(), size, RejectReason.OUT_OF_BOUNDS);
        }
        if (move.mode() != QueueMode.REPLACE) {
            return Tally.rejectAll(envelope, kind(), size, RejectReason.UNSUPPORTED);
        }
        Tally tally = new Tally();
        int[] taking = new int[size];
        int n = 0;
        for (int k = 0; k < size; k++) {
            int handle = move.units().get(k);
            RejectReason reason = Ownership.check(envelope.issuer(), handle, state);
            if (reason == null && state.garrisoned(Handle.index(handle))) {
                reason = RejectReason.INVALID_ARGUMENT;
            }
            if (reason != null) {
                tally.reject(reason);
            } else {
                taking[n++] = handle;
            }
        }
        if (n < 2) { // a formation of one is a walk
            if (n == 0) {
                return tally.result(envelope, kind());
            }
            tally.add(MoveRule.walk(
                    envelope,
                    kind(),
                    OrderKind.MOVE,
                    UnitSet.of(Arrays.copyOf(taking, n)),
                    move.target(),
                    QueueMode.REPLACE,
                    0,
                    state,
                    slots));
            return tally.result(envelope, kind());
        }
        int[] handles = Arrays.copyOf(taking, n);
        FormationController c = Formations.form(
                state, move.shape(), handles, move.target().x(), move.target().z());
        if (c == null) {
            return Tally.rejectAll(envelope, kind(), size, RejectReason.INVALID_ARGUMENT);
        }
        for (int k = 0; k < c.size(); k++) {
            int handle = c.member(k);
            state.orders().replace(Handle.index(handle), OrderKind.FORMATION, c.slotX(k), c.slotZ(k), 0, k, c.id());
            state.bus().publish(new OrderReceived(handle));
            tally.accept();
        }
        return tally.result(envelope, kind());
    }
}
