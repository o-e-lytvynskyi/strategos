package dev.strategos.core.sim.command;

import dev.strategos.core.protocol.CommandKind;
import dev.strategos.core.protocol.CommandKinds;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.SetControl;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchState;

/** One tick-time authority decision, journaled like every other player command. */
final class SetControlRule implements CommandRule<SetControl> {
    @Override
    public CommandKind<SetControl> kind() {
        return CommandKinds.SET_CONTROL;
    }

    @Override
    public CommandResult apply(CommandEnvelope envelope, SetControl command, MatchState state) {
        var authority = state.diplomacy();
        int primary = authority.primaryFaction(envelope.issuer());
        if (primary < 0) return Tally.rejectAll(envelope, kind(), 0, RejectReason.NOT_PERMITTED);
        if (!authority.teamsLocked()) return Tally.rejectAll(envelope, kind(), 0, RejectReason.UNSUPPORTED);
        int recipient = authority.primaryFaction(command.recipient());
        if (recipient < 0
                || recipient == primary
                || !authority.alliedFactions(primary, recipient)
                || state.outcome().defeated(primary)
                || state.outcome().defeated(recipient)) {
            return Tally.rejectAll(envelope, kind(), 0, RejectReason.INVALID_ARGUMENT);
        }
        state.setControl(envelope.issuer(), command.recipient(), command.granted());
        return Tally.acceptAll(envelope, kind(), 0);
    }
}
