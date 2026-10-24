package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;

/**
 * The {@code match_event} payload (MATCH-01), to every participant of match {@code matchId}: at {@code tick}, faction
 * {@code faction} was defeated ({@link #DEFEATED}), or the match ended ({@link #ENDED}), {@code faction} its winner,
 * -1 for none, after {@code ticks} ticks. UI-12's alerts join it later.
 */
@CoreApi
public record MatchNews(int matchId, long tick, int kind, int faction, long ticks) {
    public static final int DEFEATED = 0;
    public static final int ENDED = 1;

    public MatchNews {
        if (kind != DEFEATED && kind != ENDED) {
            throw new IllegalArgumentException("no match news of kind " + kind);
        }
    }

    public static final WireCodec<MatchNews> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, MatchNews n) {
            out.writeVarInt(n.matchId())
                    .writeVarLong(n.tick())
                    .writeByte(n.kind())
                    .writeZigZag(n.faction())
                    .writeVarLong(n.ticks());
        }

        @Override
        public MatchNews read(WireReader in) {
            int matchId = in.readVarInt();
            long tick = in.readVarLong();
            int kind = in.readUnsignedByte();
            if (kind != DEFEATED && kind != ENDED) {
                throw new WireException("unknown match news kind " + kind);
            }
            return new MatchNews(matchId, tick, kind, in.readZigZagInt(), in.readVarLong());
        }

        @Override
        public int maxSize() {
            return 5 + 10 + 1 + 5 + 10;
        }
    };
}
