package dev.strategos.core.sim.command;

import dev.strategos.core.protocol.CommandKind;
import dev.strategos.core.protocol.CommandKinds;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.SetStance;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchState;

/** The stance commands (CMB-05). */
final class StanceRules {
    private StanceRules() {}

    /** SetStance: each owned living unit fights as the stance says from where it stands; the others are rejected. */
    static final class SetStanceRule implements CommandRule<SetStance> {
        @Override
        public CommandKind<SetStance> kind() {
            return CommandKinds.SET_STANCE;
        }

        @Override
        public CommandResult apply(CommandEnvelope envelope, SetStance set, MatchState state) {
            UnitSet units = set.units();
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
                int index = Handle.index(handle);
                state.stances()
                        .set(
                                index,
                                set.stance(),
                                state.motion().x(index),
                                state.motion().z(index));
                tally.accept();
            }
            return tally.result(envelope, kind());
        }
    }
}
