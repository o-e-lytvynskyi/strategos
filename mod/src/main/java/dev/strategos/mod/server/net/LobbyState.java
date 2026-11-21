package dev.strategos.mod.server.net;

import dev.strategos.core.protocol.WireCodec;
import dev.strategos.core.protocol.WireException;
import dev.strategos.core.protocol.WireReader;
import dev.strategos.core.protocol.WireWriter;
import dev.strategos.mod.server.match.MatchLobby;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Private, detached recipient projection. Persistent account/connection UUIDs never cross the wire. */
public record LobbyState(
        LobbyConfiguration configuration,
        List<Member> members,
        int self,
        boolean mayConfigure,
        long revision,
        long configurationRevision,
        Phase phase,
        String reason) {
    public enum Phase {
        EDITING(0),
        PREPARING(1),
        STARTED(2),
        FAILED(3),
        CLOSED(4);
        private final int code;

        Phase(int code) {
            this.code = code;
        }
    }

    public record Member(int player, String name, boolean ready, boolean host) {
        public Member {
            if (player < 0
                    || player >= MatchLobby.MAX_SEATS
                    || (name.isBlank()
                            || name.length() > 16
                            || name.codePoints()
                                    .anyMatch(cp -> Character.isISOControl(cp) || (cp >= 0xD800 && cp <= 0xDFFF)))) {
                throw new IllegalArgumentException("invalid lobby member projection");
            }
        }
    }

    public LobbyState {
        Objects.requireNonNull(configuration);
        Objects.requireNonNull(phase);
        members = List.copyOf(members);
        if (members.isEmpty()
                || members.size() > MatchLobby.MAX_SEATS
                || self < 0
                || self >= MatchLobby.MAX_SEATS
                || revision < 0
                || configurationRevision < 0
                || configurationRevision > revision
                || reason.length() > 64
                || !reason.matches("[a-z_]*")) {
            throw new IllegalArgumentException("invalid lobby state projection");
        }
        Set<Integer> occupied = new HashSet<>();
        int hosts = 0;
        for (Member member : members) {
            if (!occupied.add(member.player())
                    || configuration.seats().stream()
                            .noneMatch(
                                    s -> s.player() == member.player() && s.kind() == LobbyConfiguration.Kind.HUMAN)) {
                throw new IllegalArgumentException("invalid projected lobby membership");
            }
            if (member.host()) hosts++;
        }
        if (!occupied.contains(self) || hosts != 1) throw new IllegalArgumentException("missing self or unique host");
    }

    public static final WireCodec<LobbyState> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, LobbyState value) {
            out.writeByte(1);
            LobbyConfiguration.WIRE.write(out, value.configuration());
            out.writeVarInt(value.members().size());
            for (Member m : value.members()) {
                out.writeByte(m.player())
                        .writeString(m.name(), 64)
                        .writeBoolean(m.ready())
                        .writeBoolean(m.host());
            }
            out.writeByte(value.self())
                    .writeBoolean(value.mayConfigure())
                    .writeVarLong(value.revision())
                    .writeVarLong(value.configurationRevision())
                    .writeByte(value.phase().code)
                    .writeString(value.reason(), 64);
        }

        @Override
        public LobbyState read(WireReader in) {
            if (in.readUnsignedByte() != 1) throw new WireException("unsupported lobby state version");
            try {
                LobbyConfiguration config = LobbyConfiguration.WIRE.read(in);
                int count = in.readVarInt(MatchLobby.MAX_SEATS);
                List<Member> members = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    members.add(
                            new Member(in.readUnsignedByte(), in.readString(64), in.readBoolean(), in.readBoolean()));
                }
                int self = in.readUnsignedByte();
                boolean mayConfigure = in.readBoolean();
                long revision = in.readVarLong();
                long configurationRevision = in.readVarLong();
                Phase phase = switch (in.readUnsignedByte()) {
                    case 0 -> Phase.EDITING;
                    case 1 -> Phase.PREPARING;
                    case 2 -> Phase.STARTED;
                    case 3 -> Phase.FAILED;
                    case 4 -> Phase.CLOSED;
                    default -> throw new WireException("unknown lobby state phase");
                };
                return new LobbyState(
                        config, members, self, mayConfigure, revision, configurationRevision, phase, in.readString(64));
            } catch (IllegalArgumentException invalid) {
                throw new WireException("invalid lobby state");
            }
        }

        @Override
        public int maxSize() {
            return 1
                    + LobbyConfiguration.WIRE.maxSize()
                    + 1
                    + MatchLobby.MAX_SEATS * (1 + 3 + 64 + 1 + 1)
                    + 1
                    + 1
                    + 10
                    + 10
                    + 1
                    + 3
                    + 64;
        }
    };
}
