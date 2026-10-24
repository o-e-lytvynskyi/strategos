package dev.strategos.core.sim.command;

import dev.strategos.core.protocol.Build;
import dev.strategos.core.protocol.CommandKind;
import dev.strategos.core.protocol.CommandKinds;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.build.BuildingStore;

/**
 * Build (M4 §1.6 row 1): the issuer's builders take a build order on an own foundation or construction, as
 * PlaceBuilding's do. An unknown building, another faction's or a finished one is refused for every unit; repair of a
 * damaged one comes with BLD-06.
 */
final class BuildRule implements CommandRule<Build> {
    @Override
    public CommandKind<Build> kind() {
        return CommandKinds.BUILD;
    }

    @Override
    public CommandResult apply(CommandEnvelope envelope, Build build, MatchState state) {
        int handle = build.building();
        BuildingStore buildings = state.buildings();
        if (!BuildingStore.isBuilding(handle) || !buildings.alive(handle)) {
            return Tally.rejectAll(envelope, kind(), build.units().size(), RejectReason.UNKNOWN_TARGET);
        }
        if (!envelope.issuer().controls(new FactionSlot(buildings.faction(handle)))) {
            return Tally.rejectAll(envelope, kind(), build.units().size(), RejectReason.NOT_OWNER);
        }
        if (buildings.state(handle) == BuildingStore.COMPLETE) {
            return Tally.rejectAll(envelope, kind(), build.units().size(), RejectReason.UNSUPPORTED);
        }
        return Builders.assign(envelope, state, build.units(), handle, state.footprintOf(handle), build.mode())
                .result(envelope, kind());
    }
}
