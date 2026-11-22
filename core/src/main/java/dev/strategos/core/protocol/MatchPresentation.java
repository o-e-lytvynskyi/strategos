package dev.strategos.core.protocol;

import dev.strategos.core.shared.PlayerSlot;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Public match identity for admitted recipients; never UUIDs, private economy or outgoing grants. */
public record MatchPresentation(
        int matchId, long revision, FactionPresentation factions, List<Participant> participants) {
    public record Participant(PlayerSlot player, int faction, int team, String name, boolean connected) {
        public Participant {
            if (player.isAdmin() || faction < 0 || faction > 15 || team < 0 || team > 15)
                throw new IllegalArgumentException("invalid human binding");
            if (name.isBlank() || name.getBytes(StandardCharsets.UTF_8).length > 64)
                throw new IllegalArgumentException("display name must be bounded and nonblank");
            for (int i = 0; i < name.length(); i++)
                if (Character.isISOControl(name.charAt(i)))
                    throw new IllegalArgumentException("control in display name");
        }

        public Participant(PlayerSlot player, int faction, String name, boolean connected) {
            this(player, faction, faction, name, connected);
        }
    }

    public MatchPresentation {
        if (matchId <= 0 || revision < 0 || participants.size() > 16)
            throw new IllegalArgumentException("invalid public context");
        Objects.requireNonNull(factions);
        participants = participants.stream()
                .sorted(Comparator.comparingInt(p -> p.player().value()))
                .toList();
        int players = 0;
        for (Participant participant : participants) {
            int bit = 1 << participant.player().value();
            if ((players & bit) != 0
                    || participant.faction() >= factions.styles().size())
                throw new IllegalArgumentException("duplicate or foreign public participant");
            players |= bit;
        }
    }

    /** The publication counter changing alone does not require another name/palette frame. */
    public boolean sameView(MatchPresentation other) {
        return matchId == other.matchId && factions.equals(other.factions) && participants.equals(other.participants);
    }

    public static final WireCodec<MatchPresentation> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, MatchPresentation value) {
            out.writeVarInt(value.matchId()).writeVarLong(value.revision());
            FactionPresentation.WIRE.write(out, value.factions());
            out.writeVarInt(value.participants().size());
            for (Participant participant : value.participants()) {
                out.writeByte(participant.player().value())
                        .writeByte(participant.faction())
                        .writeByte(participant.team())
                        .writeString(participant.name(), 64)
                        .writeBoolean(participant.connected());
            }
        }

        @Override
        public MatchPresentation read(WireReader in) {
            int match = in.readVarInt();
            long revision = in.readVarLong();
            var factions = FactionPresentation.WIRE.read(in);
            int count = in.readVarInt(16);
            var participants = new ArrayList<Participant>(count);
            try {
                for (int i = 0; i < count; i++)
                    participants.add(new Participant(
                            new PlayerSlot(in.readUnsignedByte()),
                            in.readUnsignedByte(),
                            in.readUnsignedByte(),
                            in.readString(64),
                            in.readBoolean()));
                return new MatchPresentation(match, revision, factions, participants);
            } catch (IllegalArgumentException invalid) {
                throw new WireException("invalid public match presentation");
            }
        }

        @Override
        public int maxSize() {
            return 5 + 9 + FactionPresentation.WIRE.maxSize() + 1 + 16 * (1 + 1 + 1 + 1 + 64 + 1);
        }
    };
}
