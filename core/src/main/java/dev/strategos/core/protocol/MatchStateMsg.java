package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;

/** The {@code match_state} payload: where a match is in its life, and its last completed tick. */
@CoreApi
public record MatchStateMsg(int matchId, Lifecycle lifecycle, long tick) {
    /** A match's lifecycle as the wire carries it, one name per state the server's matches go through. */
    @CoreApi
    public enum Lifecycle {
        CREATED,
        PREPARING,
        READY,
        RUNNING,
        PAUSED,
        ENDED,
        FAULTED,
        DISPOSED
    }

    public static final WireCodec<MatchStateMsg> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, MatchStateMsg value) {
            out.writeVarInt(value.matchId());
            out.writeByte(
                    switch (value.lifecycle()) {
                        case CREATED -> 0;
                        case PREPARING -> 1;
                        case READY -> 2;
                        case RUNNING -> 3;
                        case PAUSED -> 4;
                        case ENDED -> 5;
                        case FAULTED -> 6;
                        case DISPOSED -> 7;
                    });
            out.writeZigZag(value.tick());
        }

        @Override
        public MatchStateMsg read(WireReader in) {
            int id = in.readVarInt();
            int code = in.readUnsignedByte();
            Lifecycle lifecycle = switch (code) {
                case 0 -> Lifecycle.CREATED;
                case 1 -> Lifecycle.PREPARING;
                case 2 -> Lifecycle.READY;
                case 3 -> Lifecycle.RUNNING;
                case 4 -> Lifecycle.PAUSED;
                case 5 -> Lifecycle.ENDED;
                case 6 -> Lifecycle.FAULTED;
                case 7 -> Lifecycle.DISPOSED;
                default -> throw new WireException("unknown lifecycle " + code);
            };
            return new MatchStateMsg(id, lifecycle, in.readZigZagLong());
        }

        @Override
        public int maxSize() {
            return 5 + 1 + 10;
        }
    };
}
