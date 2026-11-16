package dev.strategos.mod.server.net;

import dev.strategos.core.protocol.WireCodec;
import dev.strategos.core.protocol.WireException;
import dev.strategos.core.protocol.WireReader;
import dev.strategos.core.protocol.WireWriter;
import dev.strategos.mod.server.match.MatchLobby;
import java.util.Objects;

/** A bounded intent. Authentication comes exclusively from the connection, never these bytes. */
public sealed interface LobbyAction {
    record Open(LobbyConfiguration configuration) implements LobbyAction {
        public Open {
            Objects.requireNonNull(configuration);
        }
    }

    record Edit(LobbyConfiguration configuration) implements LobbyAction {
        public Edit {
            Objects.requireNonNull(configuration);
        }
    }

    record Join(int player) implements LobbyAction {
        public Join {
            if (player < 0 || player >= MatchLobby.MAX_SEATS) throw new IllegalArgumentException("invalid lobby seat");
        }
    }

    record Ready(boolean ready, long configurationRevision) implements LobbyAction {
        public Ready {
            if (configurationRevision < 0) throw new IllegalArgumentException("negative configuration revision");
        }
    }

    record Leave() implements LobbyAction {}

    record Start() implements LobbyAction {}

    WireCodec<LobbyAction> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, LobbyAction action) {
            out.writeByte(1);
            switch (action) {
                case Open open -> {
                    out.writeByte(0);
                    LobbyConfiguration.WIRE.write(out, open.configuration());
                }
                case Edit edit -> {
                    out.writeByte(1);
                    LobbyConfiguration.WIRE.write(out, edit.configuration());
                }
                case Join join -> out.writeByte(2).writeByte(join.player());
                case Ready ready ->
                    out.writeByte(3).writeBoolean(ready.ready()).writeVarLong(ready.configurationRevision());
                case Leave ignored -> out.writeByte(4);
                case Start ignored -> out.writeByte(5);
            }
        }

        @Override
        public LobbyAction read(WireReader in) {
            if (in.readUnsignedByte() != 1) throw new WireException("unsupported lobby action version");
            try {
                return switch (in.readUnsignedByte()) {
                    case 0 -> new Open(LobbyConfiguration.WIRE.read(in));
                    case 1 -> new Edit(LobbyConfiguration.WIRE.read(in));
                    case 2 -> new Join(in.readUnsignedByte());
                    case 3 -> new Ready(in.readBoolean(), in.readVarLong());
                    case 4 -> new Leave();
                    case 5 -> new Start();
                    default -> throw new WireException("unknown lobby action");
                };
            } catch (IllegalArgumentException invalid) {
                throw new WireException("invalid lobby action");
            }
        }

        @Override
        public int maxSize() {
            return 2 + LobbyConfiguration.WIRE.maxSize();
        }
    };
}
