package dev.strategos.core.sim.command;

import dev.strategos.core.protocol.CommandKind;
import dev.strategos.core.protocol.CommandKinds;
import dev.strategos.core.protocol.DeleteUnits;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.UnitDied;

/**
 * Delete (UI-17): each owned unit leaves the match as one that lost its hit points does, in the order given, with no
 * killer: a {@link UnitDied} whose attacker is none, no loot to anyone, no notice of a loss and no count in the
 * match's losses. The player confirmed on the client; the rule only checks ownership.
 */
final class DeleteUnitsRule implements CommandRule<DeleteUnits> {
    @Override
    public CommandKind<DeleteUnits> kind() {
        return CommandKinds.DELETE_UNITS;
    }

    @Override
    public CommandResult apply(CommandEnvelope envelope, DeleteUnits delete, MatchState state) {
        UnitSet units = delete.units();
        if (units.size() == 0) {
            return Tally.rejectAll(envelope, kind(), 0, RejectReason.INVALID_ARGUMENT);
        }
        Tally tally = new Tally();
        for (int i = 0; i < units.size(); i++) {
            int handle = units.get(i);
            RejectReason reason = Ownership.check(envelope.issuer(), handle, state);
            if (reason != null) {
                tally.reject(reason);
                continue;
            }
            FactionSlot faction = new FactionSlot(state.identity().faction(Handle.index(handle)));
            state.outputs().add(new UnitDied(handle, faction, Handle.NONE));
            state.removeUnit(handle);
            tally.accept();
        }
        return tally.result(envelope, kind());
    }
}
