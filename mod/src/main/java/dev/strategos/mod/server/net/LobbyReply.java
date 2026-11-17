package dev.strategos.mod.server.net;

import dev.strategos.core.protocol.WireCodec;
import dev.strategos.core.protocol.WireException;
import dev.strategos.core.protocol.WireReader;
import dev.strategos.core.protocol.WireWriter;

/** Bounded private request failure, containing neither membership nor backend exception text. */
public enum LobbyReply {
    REFUSED(0, "refused"),
    INVALID(1, "invalid"),
    ERROR(2, "error"),
    RATE_LIMITED(3, "rate_limited");
    private final int code;
    private final String key;

    LobbyReply(int code, String key) {
        this.code = code;
        this.key = key;
    }

    public String translationKey() {
        return "strategos.lobby.reply." + key;
    }

    public static final WireCodec<LobbyReply> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, LobbyReply reply) {
            out.writeByte(1).writeByte(reply.code);
        }

        @Override
        public LobbyReply read(WireReader in) {
            if (in.readUnsignedByte() != 1) throw new WireException("unsupported lobby reply version");
            return switch (in.readUnsignedByte()) {
                case 0 -> REFUSED;
                case 1 -> INVALID;
                case 2 -> ERROR;
                case 3 -> RATE_LIMITED;
                default -> throw new WireException("unknown lobby reply");
            };
        }

        @Override
        public int maxSize() {
            return 2;
        }
    };
}
