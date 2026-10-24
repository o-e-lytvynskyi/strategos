package dev.strategos.core.sim.command;

import dev.strategos.core.protocol.AnnounceWave;
import dev.strategos.core.protocol.CommandKind;
import dev.strategos.core.protocol.CommandKinds;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.SetAi;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.WaveAnnounced;
import dev.strategos.core.sim.ai.ComputerPlayer;

/**
 * The computer players' commands (AI-01, AI-17): an admin hands a faction to a computer player, or back; a computer
 * player announces a wave, at least 30 seconds ahead, to everyone else.
 */
final class AiRules {
    private AiRules() {}

    static final class SetAiRule implements CommandRule<SetAi> {
        @Override
        public CommandKind<SetAi> kind() {
            return CommandKinds.SET_AI;
        }

        @Override
        public CommandResult apply(CommandEnvelope envelope, SetAi set, MatchState state) {
            if (!envelope.issuer().isAdmin()) {
                return Tally.rejectAll(envelope, kind(), 0, RejectReason.NOT_PERMITTED);
            }
            int faction = set.faction().value();
            if (faction >= state.config().factions()) {
                return Tally.rejectAll(envelope, kind(), 0, RejectReason.INVALID_ARGUMENT);
            }
            if (set.script().isEmpty()) {
                state.aiSlots().set(faction, null);
                return Tally.acceptAll(envelope, kind(), 0);
            }
            try {
                state.aiSlots().set(faction, new ComputerPlayer(faction, set.profile(), set.script()));
            } catch (IllegalArgumentException e) {
                return Tally.rejectAll(envelope, kind(), 0, RejectReason.INVALID_ARGUMENT);
            }
            return Tally.acceptAll(envelope, kind(), 0);
        }
    }

    static final class AnnounceWaveRule implements CommandRule<AnnounceWave> {
        @Override
        public CommandKind<AnnounceWave> kind() {
            return CommandKinds.ANNOUNCE_WAVE;
        }

        @Override
        public CommandResult apply(CommandEnvelope envelope, AnnounceWave wave, MatchState state) {
            int faction = envelope.issuer().value();
            if (envelope.issuer().isAdmin() || state.aiSlots().get(faction) == null) {
                return Tally.rejectAll(envelope, kind(), 0, RejectReason.NOT_PERMITTED);
            }
            if (wave.ticks() < AnnounceWave.LEAST) {
                return Tally.rejectAll(envelope, kind(), 0, RejectReason.INVALID_ARGUMENT);
            }
            state.outputs()
                    .add(new WaveAnnounced(new FactionSlot(faction), wave.x(), wave.z(), state.tick() + wave.ticks()));
            return Tally.acceptAll(envelope, kind(), 0);
        }
    }
}
