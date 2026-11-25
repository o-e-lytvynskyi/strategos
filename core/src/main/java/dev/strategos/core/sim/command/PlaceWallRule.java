package dev.strategos.core.sim.command;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.PlacementRules;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.content.WallLine;
import dev.strategos.core.protocol.CommandKind;
import dev.strategos.core.protocol.CommandKinds;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.PlaceWall;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchBounds;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.Placing;
import dev.strategos.core.sim.build.WallGates;
import dev.strategos.core.sim.nav.GateOverlay;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * PlaceWall (BLD-13, M9 spec §2.10): a line of {@link WallLine} segments, each laid as {@link PlaceBuildingRule} lays a
 * building: its own placement check (territory included), its own cost from the treasury, its own foundation, the
 * issuer's builders sent to the segments in order. A refused segment leaves a gap and the rest still go up; the
 * result's {@code accepted} counts the segments laid, {@code rejected} those refused, {@code firstReason} the first
 * refusal (what is missing, when it was the stock), and the outcome is partial when both are not zero. The gate
 * template of a wall template is the one whose id adds {@code _gate}.
 */
final class PlaceWallRule implements CommandRule<PlaceWall> {
    @Override
    public CommandKind<PlaceWall> kind() {
        return CommandKinds.PLACE_WALL;
    }

    @Override
    public CommandResult apply(CommandEnvelope envelope, PlaceWall place, MatchState state) {
        int faction = envelope.issuer().value();
        UnitSet builders = place.builders();
        if (envelope.issuer().isAdmin() || faction >= state.config().factions()) {
            return Tally.rejectAll(envelope, kind(), builders.size(), RejectReason.NOT_PERMITTED);
        }
        UnitTemplate wall = state.templates().byId(place.wall());
        if (wall == null) {
            return Tally.rejectAll(envelope, kind(), builders.size(), RejectReason.UNKNOWN_TEMPLATE);
        }
        int length = segmentLength(state, wall, faction);
        Blueprint wallBlueprint = state.templates().blueprint(wall.netIndex());
        if (!wall.isBuilding() || wallBlueprint == null || length < 1) {
            return Tally.rejectAll(envelope, kind(), builders.size(), RejectReason.UNSUPPORTED);
        }
        int count = WallLine.count(place.x1(), place.z1(), place.x2(), place.z2(), length);
        if (count < 1 || count > PlaceWall.MAX_SEGMENTS) {
            return Tally.rejectAll(envelope, kind(), builders.size(), RejectReason.INVALID_ARGUMENT);
        }
        for (int gate : place.gates()) {
            if (gate < 0 || gate >= count) {
                return Tally.rejectAll(envelope, kind(), builders.size(), RejectReason.INVALID_ARGUMENT);
            }
        }
        UnitTemplate gate = place.gates().isEmpty()
                ? wall
                : state.templates().byId(new TemplateId(place.wall().value() + WallLine.GATE_SUFFIX));
        Blueprint gateBlueprint = gate == null ? null : state.templates().blueprint(gate.netIndex());
        if (gate == null || gateBlueprint == null || !gate.isBuilding()) {
            return Tally.rejectAll(envelope, kind(), builders.size(), RejectReason.UNSUPPORTED);
        }
        if (!Placing.listed(state, wall.netIndex(), faction) || !Placing.listed(state, gate.netIndex(), faction)) {
            return Tally.rejectAll(envelope, kind(), builders.size(), RejectReason.REQUIREMENTS);
        }
        MatchBounds bounds = state.config().bounds();
        WallLine.Floor floor = (x, z) -> x < 0 || z < 0 || x >= bounds.sizeX() || z >= bounds.sizeZ()
                ? PlacementRules.NO_LAND
                : Placing.floor(state.surfaces(), bounds, x, z);
        List<WallLine.Segment> segments =
                WallLine.of(place.x1(), place.z1(), place.x2(), place.z2(), length, place.gates(), floor);
        PlacementRules.World world = Placing.world(state, faction); // segments never overlap: one reading serves all
        int gatesStanding = standingAndQueuedGates(state);
        int laid = 0;
        int refused = 0;
        RejectReason first = null;
        long @Nullable [] missing = null;
        for (WallLine.Segment segment : segments) {
            UnitTemplate template = segment.gate() ? gate : wall;
            Blueprint b = segment.gate() ? gateBlueprint : wallBlueprint;
            if (segment.gate() && gatesStanding >= GateOverlay.MAX_GATES) {
                refused++; // the overlay holds no more: never a gate that finishes as a plain wall
                if (first == null) {
                    first = RejectReason.LIMIT;
                }
                continue;
            }
            Footprint at = Footprint.of(b, segment.x(), segment.z(), segment.rotation());
            PlacementRules.Verdict verdict = PlacementRules.check(
                    state.civs().rules(faction).placement(),
                    world,
                    b,
                    at,
                    faction,
                    Placing.params(state, template.netIndex(), faction));
            PlacementRules.Rule failed = verdict.failed();
            if (failed != null) {
                refused++;
                if (first == null) {
                    first = PlaceBuildingRule.reason(failed);
                    if (failed == PlacementRules.Rule.AFFORDABLE) {
                        missing = state.treasury()
                                .missing(faction, state.costs().of(state.stats(), template.netIndex(), faction));
                    }
                }
                continue;
            }
            state.treasury().pay(faction, state.costs().of(state.stats(), template.netIndex(), faction));
            int baseY = segment.baseY() == Integer.MIN_VALUE ? Placing.baseY(state, b, at) : segment.baseY();
            int handle = state.buildings().create(template.netIndex(), faction, at, baseY, BuildingStore.FOUNDATION, 0);
            state.buildings().setSlots(handle, Placing.slots(state, at));
            if (builders.size() > 0) {
                Builders.assign(envelope, state, builders, handle, at, laid == 0 ? QueueMode.REPLACE : QueueMode.QUEUE);
            }
            laid++;
            gatesStanding += segment.gate() ? 1 : 0;
        }
        CommandOutcome outcome =
                refused == 0 ? CommandOutcome.ACCEPTED : laid > 0 ? CommandOutcome.PARTIAL : CommandOutcome.REJECTED;
        return new CommandResult(
                envelope.issuer(),
                envelope.clientSeq(),
                kind().id(),
                outcome,
                laid,
                refused,
                first,
                missing == null
                        ? List.of()
                        : java.util.Arrays.stream(missing).boxed().toList());
    }

    // The gates in the overlay plus the gate buildings still to be finished, which will join it.
    private static int standingAndQueuedGates(MatchState state) {
        int n = state.play().gates().count();
        BuildingStore b = state.buildings();
        for (int handle : b.handles()) {
            if (b.state(handle) != BuildingStore.COMPLETE && WallGates.isGate(state, handle)) {
                n++;
            }
        }
        return n;
    }

    // How many blocks a segment of the wall template is long, its Wall/segment; 0 for a template that is no wall.
    private static int segmentLength(MatchState state, UnitTemplate wall, int faction) {
        int p = state.stats().path(WallLine.SEGMENT);
        double v = p < 0 ? Double.NaN : state.stats().blockValue(wall.netIndex(), faction, p);
        return Double.isFinite(v) ? (int) Math.round(v) : 0;
    }
}
