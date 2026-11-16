package dev.strategos.mod.server.net;

import dev.strategos.core.protocol.SkirmishSetup;
import dev.strategos.core.protocol.WireCodec;
import dev.strategos.core.protocol.WireException;
import dev.strategos.core.protocol.WireReader;
import dev.strategos.core.protocol.WireWriter;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.mod.server.match.MatchLobby;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Configuration carried by client intents; occupants and ready acknowledgements are never part of it. */
public record LobbyConfiguration(int factionCapacity, List<Seat> seats, SkirmishSetup setup) {
    public enum Kind {
        HUMAN(0),
        AI(1),
        CLOSED(2);
        private final int code;

        Kind(int code) {
            this.code = code;
        }
    }

    public record Seat(int player, Kind kind, int faction, int team, String civilisation, int colour) {
        public Seat {
            Objects.requireNonNull(kind);
            // Share the aggregate's configuration rules; this detached seat assigns no UUID.
            new MatchLobby.Seat(
                    new PlayerSlot(player),
                    matchKind(kind),
                    null,
                    new FactionSlot(faction),
                    team,
                    civilisation,
                    colour);
        }
    }

    public LobbyConfiguration {
        seats = List.copyOf(seats);
        Objects.requireNonNull(setup);
        MatchLobby.create(
                factionCapacity,
                seats.stream()
                        .map(s -> new MatchLobby.Seat(
                                new PlayerSlot(s.player()),
                                matchKind(s.kind()),
                                null,
                                new FactionSlot(s.faction()),
                                s.team(),
                                s.civilisation(),
                                s.colour()))
                        .toList());
    }

    public static MatchLobby.Kind matchKind(Kind kind) {
        return switch (kind) {
            case HUMAN -> MatchLobby.Kind.HUMAN;
            case AI -> MatchLobby.Kind.AI;
            case CLOSED -> MatchLobby.Kind.CLOSED;
        };
    }

    public static final WireCodec<LobbyConfiguration> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, LobbyConfiguration value) {
            out.writeByte(value.factionCapacity()).writeVarInt(value.seats().size());
            for (Seat s : value.seats()) {
                out.writeByte(s.player())
                        .writeByte(s.kind().code)
                        .writeByte(s.faction())
                        .writeByte(s.team())
                        .writeString(s.civilisation(), 128)
                        .writeByte(s.colour());
            }
            SkirmishSetup.WIRE.write(out, value.setup());
        }

        @Override
        public LobbyConfiguration read(WireReader in) {
            try {
                int capacity = in.readUnsignedByte();
                int count = in.readVarInt(MatchLobby.MAX_SEATS);
                List<Seat> seats = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    int player = in.readUnsignedByte();
                    Kind kind = switch (in.readUnsignedByte()) {
                        case 0 -> Kind.HUMAN;
                        case 1 -> Kind.AI;
                        case 2 -> Kind.CLOSED;
                        default -> throw new WireException("unknown lobby seat kind");
                    };
                    seats.add(new Seat(
                            player,
                            kind,
                            in.readUnsignedByte(),
                            in.readUnsignedByte(),
                            in.readString(128),
                            in.readUnsignedByte()));
                }
                return new LobbyConfiguration(capacity, seats, SkirmishSetup.WIRE.read(in));
            } catch (IllegalArgumentException invalid) {
                throw new WireException("invalid lobby configuration");
            }
        }

        @Override
        public int maxSize() {
            return 1 + 1 + MatchLobby.MAX_SEATS * (1 + 1 + 1 + 1 + 3 + 128 + 1) + SkirmishSetup.WIRE.maxSize();
        }
    };
}
