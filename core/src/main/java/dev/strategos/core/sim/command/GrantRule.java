package dev.strategos.core.sim.command;

import dev.strategos.core.protocol.CommandKind;
import dev.strategos.core.protocol.CommandKinds;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.econ.Amount;

/**
 * Grant, admin only (ECO-01): adds whole units of a resource to a seated faction's treasury, as granted. A resource the
 * match does not have, a faction it does not seat, or an amount outside 1 to a billion is an invalid argument.
 */
final class GrantRule implements CommandRule<Grant> {
    @Override
    public CommandKind<Grant> kind() {
        return CommandKinds.GRANT;
    }

    @Override
    public CommandResult apply(CommandEnvelope envelope, Grant grant, MatchState state) {
        if (!envelope.issuer().isAdmin()) {
            return Tally.rejectAll(envelope, kind(), 0, RejectReason.NOT_PERMITTED);
        }
        int resource = state.config().rules().economy().indexOf(grant.resource());
        if (resource < 0
                || grant.faction().value() >= state.config().factions()
                || grant.amount() < 1
                || grant.amount() > Grant.MAX) {
            return Tally.rejectAll(envelope, kind(), 0, RejectReason.INVALID_ARGUMENT);
        }
        state.treasury().grant(grant.faction().value(), resource, grant.amount() * Amount.UNIT);
        return Tally.acceptAll(envelope, kind(), 0);
    }
}
