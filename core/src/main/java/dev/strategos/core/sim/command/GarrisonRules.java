package dev.strategos.core.sim.command;

import dev.strategos.core.protocol.CommandKind;
import dev.strategos.core.protocol.CommandKinds;
import dev.strategos.core.protocol.Garrison;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.Ungarrison;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.garrison.Garrisons;
import dev.strategos.core.sim.order.OrderKind;
import org.jspecify.annotations.Nullable;

/** The garrison commands (BLD-16). */
final class GarrisonRules {
    private GarrisonRules() {}

    /** The refusal that stops a command naming building {@code handle} for every unit, or null: it is the issuer's own. */
    private static @Nullable RejectReason building(CommandEnvelope envelope, MatchState state, int handle) {
        BuildingStore buildings = state.buildings();
        if (!BuildingStore.isBuilding(handle) || !buildings.alive(handle)) {
            return RejectReason.UNKNOWN_TARGET;
        }
        if (!envelope.issuer().controls(new FactionSlot(buildings.faction(handle)))) {
            return RejectReason.NOT_OWNER;
        }
        return null;
    }

    /**
     * Garrison: each owned living unit of the building's faction and of a class it admits walks to its door and goes in
     * (its order replaces the others); a unit already inside is refused, and a full building fails the order on arrival.
     */
    static final class GarrisonRule implements CommandRule<Garrison> {
        @Override
        public CommandKind<Garrison> kind() {
            return CommandKinds.GARRISON;
        }

        @Override
        public CommandResult apply(CommandEnvelope envelope, Garrison garrison, MatchState state) {
            UnitSet units = garrison.units();
            if (units.size() == 0) {
                return Tally.rejectAll(envelope, kind(), 0, RejectReason.INVALID_ARGUMENT);
            }
            int handle = garrison.building();
            RejectReason refusal = building(envelope, state, handle);
            if (refusal != null) {
                return Tally.rejectAll(envelope, kind(), units.size(), refusal);
            }
            if (state.buildings().state(handle) != BuildingStore.COMPLETE || Garrisons.capacity(state, handle) == 0) {
                return Tally.rejectAll(envelope, kind(), units.size(), RejectReason.UNSUPPORTED);
            }
            int[] door = Garrisons.door(state, handle);
            if (door == null) {
                return Tally.rejectAll(envelope, kind(), units.size(), RejectReason.BLOCKED);
            }
            Tally tally = new Tally();
            for (int k = 0; k < units.size(); k++) {
                int unit = units.get(k);
                RejectReason reason = Ownership.check(envelope.issuer(), unit, state);
                if (reason != null) {
                    tally.reject(reason);
                    continue;
                }
                int i = Handle.index(unit);
                if (state.identity().faction(i) != state.buildings().faction(handle)) {
                    tally.reject(RejectReason.NOT_OWNER);
                } else if (state.garrison().inside(i)) {
                    tally.reject(RejectReason.INVALID_ARGUMENT);
                } else if (!Garrisons.admits(state, i, handle)) {
                    tally.reject(RejectReason.NOT_PERMITTED);
                } else {
                    state.orders().replace(i, OrderKind.GARRISON, door[0] + 0.5, door[1] + 0.5, 0, 0, handle);
                    state.bus().publish(new OrderReceived(unit));
                    tally.accept();
                }
            }
            return tally.result(envelope, kind());
        }
    }

    /**
     * Ungarrison: the named units inside the building, or all of them when none are named, come out at its door in the
     * order they went in. A named unit that is not inside it is refused.
     */
    static final class UngarrisonRule implements CommandRule<Ungarrison> {
        @Override
        public CommandKind<Ungarrison> kind() {
            return CommandKinds.UNGARRISON;
        }

        @Override
        public CommandResult apply(CommandEnvelope envelope, Ungarrison ungarrison, MatchState state) {
            UnitSet named = ungarrison.units();
            int handle = ungarrison.building();
            RejectReason refusal = building(envelope, state, handle);
            if (refusal != null) {
                return Tally.rejectAll(envelope, kind(), Math.max(1, named.size()), refusal);
            }
            int[] inside = state.garrison().occupants(handle);
            if (named.size() == 0) {
                Garrisons.release(state, handle, inside);
                return Tally.acceptAll(envelope, kind(), inside.length);
            }
            Tally tally = new Tally();
            int[] leaving = new int[inside.length];
            int n = 0;
            for (int k = 0; k < named.size(); k++) {
                int unit = named.get(k);
                RejectReason reason = Ownership.check(envelope.issuer(), unit, state);
                if (reason != null) {
                    tally.reject(reason);
                } else if (state.garrison().buildingOf(Handle.index(unit)) != handle) {
                    tally.reject(RejectReason.INVALID_ARGUMENT);
                } else {
                    tally.accept();
                }
            }
            for (int unit : inside) { // in the order they went in
                if (named.contains(unit)) {
                    leaving[n++] = unit;
                }
            }
            Garrisons.release(state, handle, java.util.Arrays.copyOf(leaving, n));
            return tally.result(envelope, kind());
        }
    }
}
