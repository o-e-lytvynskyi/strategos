package dev.strategos.core.sim.command;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.CommandKind;
import dev.strategos.core.protocol.CommandKinds;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.SpawnDeposit;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchBounds;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.econ.Deposits;

/**
 * SpawnDeposit, admin only (ECO-08): a deposit template's mine or quarry where a map or a scenario puts it, its
 * footprint inside the match space and on nobody's building; a template that is no deposit is unsupported.
 */
final class SpawnDepositRule implements CommandRule<SpawnDeposit> {
    @Override
    public CommandKind<SpawnDeposit> kind() {
        return CommandKinds.SPAWN_DEPOSIT;
    }

    @Override
    public CommandResult apply(CommandEnvelope envelope, SpawnDeposit spawn, MatchState state) {
        if (!envelope.issuer().isAdmin()) {
            return Tally.rejectAll(envelope, kind(), 0, RejectReason.NOT_PERMITTED);
        }
        UnitTemplate template = state.templates().byId(spawn.template());
        if (template == null) {
            return Tally.rejectAll(envelope, kind(), 0, RejectReason.UNKNOWN_TEMPLATE);
        }
        if (!Deposits.isDeposit(state.templates(), template.netIndex())) {
            return Tally.rejectAll(envelope, kind(), 0, RejectReason.UNSUPPORTED);
        }
        Blueprint b = java.util.Objects.requireNonNull(state.templates().blueprint(template.netIndex()));
        Footprint at = Footprint.of(b, spawn.x(), spawn.z(), 0);
        MatchBounds bounds = state.config().bounds();
        for (int cell : at.columns(b)) {
            int x = cell >>> 16;
            int z = cell & 0xFFFF;
            if (spawn.x() < 0 || spawn.z() < 0 || x >= bounds.sizeX() || z >= bounds.sizeZ()) {
                return Tally.rejectAll(envelope, kind(), 0, RejectReason.OUT_OF_BOUNDS);
            }
            if (state.footprintAt(x, z)) {
                return Tally.rejectAll(envelope, kind(), 0, RejectReason.BLOCKED);
            }
        }
        Deposits.spawn(state, template.netIndex(), at);
        return Tally.acceptAll(envelope, kind(), 0);
    }
}
