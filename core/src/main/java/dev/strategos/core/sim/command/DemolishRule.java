package dev.strategos.core.sim.command;

import dev.strategos.core.protocol.CommandKind;
import dev.strategos.core.protocol.CommandKinds;
import dev.strategos.core.protocol.Demolish;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.sim.BuildingDestroyed;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.build.BuildingLife;
import dev.strategos.core.sim.build.BuildingStore;

/**
 * Demolish (BLD-09): an own finished building falls as one that lost its hit points does, its blocks through the
 * writer, its housing, dropsite and queue gone with it; no refund. A site is refused as unsupported (it is cancelled).
 */
final class DemolishRule implements CommandRule<Demolish> {
    @Override
    public CommandKind<Demolish> kind() {
        return CommandKinds.DEMOLISH;
    }

    @Override
    public CommandResult apply(CommandEnvelope envelope, Demolish demolish, MatchState state) {
        int handle = demolish.building();
        BuildingStore buildings = state.buildings();
        if (!BuildingStore.isBuilding(handle) || !buildings.alive(handle)) {
            return Tally.rejectAll(envelope, kind(), 1, RejectReason.UNKNOWN_TARGET);
        }
        FactionSlot faction = new FactionSlot(buildings.faction(handle));
        if (!envelope.issuer().controls(faction)) {
            return Tally.rejectAll(envelope, kind(), 1, RejectReason.NOT_OWNER);
        }
        if (buildings.state(handle) != BuildingStore.COMPLETE) {
            return Tally.rejectAll(envelope, kind(), 1, RejectReason.UNSUPPORTED);
        }
        state.outputs().add(new BuildingDestroyed(handle, faction));
        BuildingLife.destroy(state, handle);
        return Tally.acceptAll(envelope, kind(), 1);
    }
}
