package dev.strategos.core.sim.command;

import dev.strategos.core.protocol.CancelBuilding;
import dev.strategos.core.protocol.CommandKind;
import dev.strategos.core.protocol.CommandKinds;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.build.BuildingLife;
import dev.strategos.core.sim.build.BuildingStore;

/**
 * CancelBuilding (BLD-09): an own foundation or construction goes, its blocks through the writer, and its cost comes
 * back but for the share built, {@link BuildingLife#refund}. A finished building is refused as unsupported (it is demolished),
 * another faction's as not its owner's, an unknown handle as an unknown target.
 */
final class CancelBuildingRule implements CommandRule<CancelBuilding> {
    @Override
    public CommandKind<CancelBuilding> kind() {
        return CommandKinds.CANCEL_BUILDING;
    }

    @Override
    public CommandResult apply(CommandEnvelope envelope, CancelBuilding cancel, MatchState state) {
        int handle = cancel.building();
        BuildingStore buildings = state.buildings();
        if (!BuildingStore.isBuilding(handle) || !buildings.alive(handle)) {
            return Tally.rejectAll(envelope, kind(), 1, RejectReason.UNKNOWN_TARGET);
        }
        int faction = buildings.faction(handle);
        if (!envelope.issuer().controls(new FactionSlot(faction))) {
            return Tally.rejectAll(envelope, kind(), 1, RejectReason.NOT_OWNER);
        }
        if (buildings.state(handle) == BuildingStore.COMPLETE) {
            return Tally.rejectAll(envelope, kind(), 1, RejectReason.UNSUPPORTED);
        }
        long[] cost = state.costs().of(state.stats(), buildings.template(handle), faction);
        state.treasury().refund(faction, BuildingLife.refund(cost, buildings.progress(handle)));
        BuildingLife.destroy(state, handle);
        return Tally.acceptAll(envelope, kind(), 1);
    }
}
