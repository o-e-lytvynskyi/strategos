package dev.strategos.core.sim.command;

import dev.strategos.core.protocol.CommandKind;
import dev.strategos.core.protocol.CommandKinds;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.Stop;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.StopOrdered;

/** Stop: each owned unit loses its order; the unit's AI goes idle from the bus. */
final class StopRule implements CommandRule<Stop> {
    @Override
    public CommandKind<Stop> kind() {
        return CommandKinds.STOP;
    }

    @Override
    public CommandResult apply(CommandEnvelope envelope, Stop stop, MatchState state) {
        UnitSet units = stop.units();
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
            state.orders().clear(Handle.index(handle));
            state.bus().publish(new StopOrdered(handle));
            tally.accept();
        }
        return tally.result(envelope, kind());
    }
}
