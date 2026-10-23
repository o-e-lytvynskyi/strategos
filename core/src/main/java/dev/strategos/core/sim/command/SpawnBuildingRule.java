package dev.strategos.core.sim.command;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.CommandKind;
import dev.strategos.core.protocol.CommandKinds;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchBounds;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.nav.Surface;
import dev.strategos.core.sim.nav.SurfaceGrid;

/**
 * SpawnBuilding, admin only (BLD-01): a finished building of a building template for a seated faction, its corner at
 * a cell and turned in quarters; the whole footprint inside the match space and on nobody else's. Its blueprint's
 * lowest layer takes the place of the ground's top block, at the highest standing height under the footprint (the
 * flat floor where no page is in yet); levelling uneven ground is BLD-04's.
 */
final class SpawnBuildingRule implements CommandRule<SpawnBuilding> {
    @Override
    public CommandKind<SpawnBuilding> kind() {
        return CommandKinds.SPAWN_BUILDING;
    }

    @Override
    public CommandResult apply(CommandEnvelope envelope, SpawnBuilding spawn, MatchState state) {
        if (!envelope.issuer().isAdmin()) {
            return Tally.rejectAll(envelope, kind(), 0, RejectReason.NOT_PERMITTED);
        }
        UnitTemplate template = state.templates().byId(spawn.template());
        if (template == null) {
            return Tally.rejectAll(envelope, kind(), 0, RejectReason.UNKNOWN_TEMPLATE);
        }
        Blueprint b = state.templates().blueprint(template.netIndex());
        if (!template.isBuilding() || b == null) {
            return Tally.rejectAll(envelope, kind(), 0, RejectReason.UNSUPPORTED);
        }
        if (spawn.faction().value() >= state.config().factions() || spawn.rotation() < 0 || spawn.rotation() > 3) {
            return Tally.rejectAll(envelope, kind(), 0, RejectReason.INVALID_ARGUMENT);
        }
        Footprint at = Footprint.of(b, spawn.x(), spawn.z(), spawn.rotation());
        MatchBounds bounds = state.config().bounds();
        int[] columns = at.columns(b);
        int stand = Integer.MIN_VALUE;
        SurfaceGrid grid = state.surfaces();
        for (int cell : columns) {
            int x = cell >>> 16;
            int z = cell & 0xFFFF;
            if (x >= bounds.sizeX() || z >= bounds.sizeZ() || spawn.x() < 0 || spawn.z() < 0) {
                return Tally.rejectAll(envelope, kind(), 0, RejectReason.OUT_OF_BOUNDS);
            }
            if (state.footprintAt(x, z)) {
                return Tally.rejectAll(envelope, kind(), 0, RejectReason.INVALID_ARGUMENT);
            }
            if (grid.count(x, z) > 0) {
                int top = grid.surface(x, z, grid.count(x, z) - 1);
                stand = Math.max(stand, bounds.minY() + (int) Math.round(Surface.floor(top) / 16.0));
            }
        }
        if (stand == Integer.MIN_VALUE) {
            stand = (int) Math.round(bounds.flatFloorY());
        }
        state.placeComplete(template.netIndex(), spawn.faction().value(), at, stand - 1);
        return Tally.acceptAll(envelope, kind(), 0);
    }
}
