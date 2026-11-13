package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;
import org.jspecify.annotations.Nullable;

/**
 * The {@code match_state} payload: where a match is in its life, its last completed tick, and at
 * {@link Lifecycle#ENDED} the match results (M6 §2.3).
 */
@CoreApi
public record MatchStateMsg(
        int matchId,
        Lifecycle lifecycle,
        long tick,
        @Nullable MatchResults results) {
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

    public MatchStateMsg(int matchId, Lifecycle lifecycle, long tick) {
        this(matchId, lifecycle, tick, null);
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
            if (value.results() != null) {
                MatchResults.WIRE.write(out, value.results());
            }
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
            long tick = in.readZigZagLong();
            MatchResults results =
                    lifecycle == Lifecycle.ENDED && in.remaining() > 0 ? MatchResults.WIRE.read(in) : null;
            return new MatchStateMsg(id, lifecycle, tick, results);
        }

        @Override
        public int maxSize() {
            return 5 + 1 + 10 + MatchResults.WIRE.maxSize();
        }
    };
}
