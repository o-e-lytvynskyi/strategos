package dev.strategos.core.sim.command;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.PlacementRules;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.CommandKind;
import dev.strategos.core.protocol.CommandKinds;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.Placing;

/**
 * PlaceBuilding (BLD-02): a seated player's faction places a building template where {@link PlacementRules} allow, as
 * the server's own world says; a refusal names the first rule that fails. The cost is paid and the foundation laid,
 * with its builder slots round it; the issuer's builders among {@code builders} get a build order on it, joined by the
 * command's mode, and a unit that does not build is refused as unsupported. A foundation is no obstruction: the first
 * builder's arrival commits it (BLD-03).
 */
final class PlaceBuildingRule implements CommandRule<PlaceBuilding> {
    @Override
    public CommandKind<PlaceBuilding> kind() {
        return CommandKinds.PLACE_BUILDING;
    }

    @Override
    public CommandResult apply(CommandEnvelope envelope, PlaceBuilding place, MatchState state) {
        int faction = envelope.issuer().value();
        UnitSet builders = place.builders();
        if (envelope.issuer().isAdmin() || faction >= state.config().factions()) {
            return Tally.rejectAll(envelope, kind(), builders.size(), RejectReason.NOT_PERMITTED);
        }
        UnitTemplate template = state.templates().byId(place.template());
        if (template == null) {
            return Tally.rejectAll(envelope, kind(), builders.size(), RejectReason.UNKNOWN_TEMPLATE);
        }
        Blueprint b = state.templates().blueprint(template.netIndex());
        if (!template.isBuilding() || b == null) {
            return Tally.rejectAll(envelope, kind(), builders.size(), RejectReason.UNSUPPORTED);
        }
        if (place.rotation() < 0 || place.rotation() > 3) {
            return Tally.rejectAll(envelope, kind(), builders.size(), RejectReason.INVALID_ARGUMENT);
        }
        Footprint at = Footprint.of(b, place.x(), place.z(), place.rotation());
        PlacementRules.Verdict verdict = PlacementRules.check(
                Placing.world(state), b, at, faction, Placing.params(state, template.netIndex(), faction));
        PlacementRules.Rule failed = verdict.failed();
        long[] cost = state.costs().of(state.stats(), template.netIndex(), faction);
        if (failed == PlacementRules.Rule.AFFORDABLE) {
            long[] missing = state.treasury().missing(faction, cost);
            return Tally.unaffordable(envelope, kind(), builders.size(), missing == null ? new long[0] : missing);
        }
        if (failed != null) {
            return Tally.rejectAll(envelope, kind(), builders.size(), reason(failed));
        }
        state.treasury().pay(faction, cost);
        int handle = state.buildings()
                .create(template.netIndex(), faction, at, Placing.baseY(state, b, at), BuildingStore.FOUNDATION, 0);
        state.buildings().setSlots(handle, Placing.slots(state, at));
        Tally tally = Builders.assign(envelope, state, builders, handle, at, place.mode());
        if (builders.size() == 0) {
            return Tally.acceptAll(envelope, kind(), 0); // a foundation for later, with no builder yet
        }
        return tally.result(envelope, kind());
    }

    /** The refusal a failed rule gives. */
    static RejectReason reason(PlacementRules.Rule rule) {
        return switch (rule) {
            case INSIDE -> RejectReason.OUT_OF_BOUNDS;
            case FREE -> RejectReason.BLOCKED;
            case LAND -> RejectReason.NOT_ON_LAND;
            case EXPLORED -> RejectReason.NOT_EXPLORED;
            case FLAT -> RejectReason.NOT_FLAT;
            case SPACING -> RejectReason.TOO_CLOSE;
            case REACH -> RejectReason.OUT_OF_REACH;
            case AFFORDABLE -> RejectReason.NOT_ENOUGH_RESOURCES;
        };
    }
}
