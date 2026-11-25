package dev.strategos.core.sim.command;

import dev.strategos.core.content.Footprint;
import dev.strategos.core.protocol.Capture;
import dev.strategos.core.protocol.CommandKind;
import dev.strategos.core.protocol.CommandKinds;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.capture.CaptureSystem;
import dev.strategos.core.sim.order.OrderKind;

/**
 * Capture (CMB-10): each owned unit with {@code Capture/strength} gets the order to capture the building, replacing its
 * others, and walks into reach of it. A building that is not alive is refused as unknown (as Attack does), one of a
 * template with no {@code Capturable/points} as unsupported; one of the unit's own faction or an ally's as an invalid
 * argument (as Attack refuses an own target); a unit with no strength as unsupported, so a selection of workers and
 * rams skips the rams and a selection of rams alone is refused.
 */
final class CaptureRule implements CommandRule<Capture> {
    @Override
    public CommandKind<Capture> kind() {
        return CommandKinds.CAPTURE;
    }

    @Override
    public CommandResult apply(CommandEnvelope envelope, Capture capture, MatchState state) {
        UnitSet units = capture.units();
        if (units.size() == 0) {
            return Tally.rejectAll(envelope, kind(), 0, RejectReason.INVALID_ARGUMENT);
        }
        int target = capture.building();
        if (!BuildingStore.isBuilding(target) || !state.buildings().alive(target)) {
            return Tally.rejectAll(envelope, kind(), units.size(), RejectReason.UNKNOWN_TARGET);
        }
        if (CaptureSystem.total(state, target) <= 0) {
            return Tally.rejectAll(envelope, kind(), units.size(), RejectReason.UNSUPPORTED);
        }
        int owner = state.buildings().faction(target);
        Footprint at = state.footprintOf(target);
        double x = at.anchorX() + at.spanX() / 2.0;
        double z = at.anchorZ() + at.spanZ() / 2.0;
        Tally tally = new Tally();
        for (int k = 0; k < units.size(); k++) {
            int handle = units.get(k);
            RejectReason reason = Ownership.check(envelope.issuer(), handle, state);
            if (reason != null) {
                tally.reject(reason);
                continue;
            }
            int i = Handle.index(handle);
            if (state.allies().allied(state.identity().faction(i), owner)) {
                tally.reject(RejectReason.INVALID_ARGUMENT);
            } else if (CaptureSystem.strength(state, i) <= 0) {
                tally.reject(RejectReason.UNSUPPORTED);
            } else {
                state.orders().replace(i, OrderKind.CAPTURE, x, z, 0, 0, target);
                state.bus().publish(new OrderReceived(handle));
                tally.accept();
            }
        }
        return tally.result(envelope, kind());
    }
}
