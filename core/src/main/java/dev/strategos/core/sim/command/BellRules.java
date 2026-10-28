package dev.strategos.core.sim.command;

import dev.strategos.core.protocol.AllClear;
import dev.strategos.core.protocol.CommandKind;
import dev.strategos.core.protocol.CommandKinds;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.RingBell;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.garrison.Bell;
import dev.strategos.core.sim.garrison.Garrisons;
import org.jspecify.annotations.Nullable;

/** The town bell's commands (BLD-16). */
final class BellRules {
    private BellRules() {}

    /** The refusal that stops a bell command at building {@code handle}, or null: an own finished holder. */
    private static @Nullable RejectReason refusal(CommandEnvelope envelope, MatchState state, int handle) {
        RejectReason reason = GarrisonRules.building(envelope, state, handle);
        if (reason != null) {
            return reason;
        }
        if (state.buildings().state(handle) != BuildingStore.COMPLETE || Garrisons.capacity(state, handle) == 0) {
            return RejectReason.UNSUPPORTED;
        }
        return null;
    }

    /**
     * Ring the bell: every own worker in reach is sent into the nearest holder with room, before its other orders. The
     * result counts the workers sent as accepted and those with no place to go as refused; a ring that found no worker
     * is accepted with none.
     */
    static final class RingBellRule implements CommandRule<RingBell> {
        @Override
        public CommandKind<RingBell> kind() {
            return CommandKinds.RING_BELL;
        }

        @Override
        public CommandResult apply(CommandEnvelope envelope, RingBell ring, MatchState state) {
            RejectReason reason = refusal(envelope, state, ring.building());
            if (reason != null) {
                return Tally.rejectAll(envelope, kind(), 1, reason);
            }
            Bell.Rung rung = Bell.ring(state, ring.building());
            Tally tally = new Tally();
            for (int k = 0; k < rung.sent(); k++) {
                tally.accept();
            }
            for (int k = 0; k < rung.refused(); k++) {
                tally.reject(RejectReason.BLOCKED);
            }
            return rung.sent() == 0 && rung.refused() == 0
                    ? Tally.acceptAll(envelope, kind(), 0)
                    : tally.result(envelope, kind());
        }
    }

    /** All clear: the bell's orders of the building's faction end, and the units it called in come out. */
    static final class AllClearRule implements CommandRule<AllClear> {
        @Override
        public CommandKind<AllClear> kind() {
            return CommandKinds.ALL_CLEAR;
        }

        @Override
        public CommandResult apply(CommandEnvelope envelope, AllClear clear, MatchState state) {
            RejectReason reason = refusal(envelope, state, clear.building());
            if (reason != null) {
                return Tally.rejectAll(envelope, kind(), 1, reason);
            }
            int affected = Bell.allClear(state, state.buildings().faction(clear.building()));
            return Tally.acceptAll(envelope, kind(), affected);
        }
    }
}
