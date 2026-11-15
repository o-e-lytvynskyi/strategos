package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;

/**
 * The {@code match_event} payload (MATCH-01, UI-12): at {@code tick}, faction {@code faction} was defeated ({@link
 * #DEFEATED}), or the match ended ({@link #ENDED}), {@code faction} its winner, -1 for none, after {@code ticks} ticks,
 * both to every participant of match {@code matchId}; or {@code faction} is under attack at match-local ({@code x},
 * {@code z}) ({@link #ALERT}), to that faction's players only; or {@code faction}'s computer player announced a wave
 * mustering at ({@code x}, {@code z}) that leaves at tick {@code ticks} ({@link #WAVE}), to every participant; or {@code
 * faction} reached a phase ({@link #PHASE}): {@code ticks} carries the phase's ordinal (1 for Town) and there is no
 * place, to every participant.
 */
@CoreApi
public record MatchNews(int matchId, long tick, int kind, int faction, long ticks, float x, float z) {
    public static final int DEFEATED = 0;
    public static final int ENDED = 1;
    public static final int ALERT = 2;
    public static final int WAVE = 3;
    public static final int PHASE = 4;

    /** A defeat or the end, at no place. */
    public MatchNews(int matchId, long tick, int kind, int faction, long ticks) {
        this(matchId, tick, kind, faction, ticks, 0, 0);
    }

    public MatchNews {
        if (kind < DEFEATED || kind > PHASE) {
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
            if (n.kind() == ALERT || n.kind() == WAVE) {
                out.writeFloat(n.x()).writeFloat(n.z());
            }
        }

        @Override
        public MatchNews read(WireReader in) {
            int matchId = in.readVarInt();
            long tick = in.readVarLong();
            int kind = in.readUnsignedByte();
            if (kind < DEFEATED || kind > PHASE) {
                throw new WireException("unknown match news kind " + kind);
            }
            int faction = in.readZigZagInt();
            long ticks = in.readVarLong();
            return kind == ALERT || kind == WAVE
                    ? new MatchNews(matchId, tick, kind, faction, ticks, in.readFloat(), in.readFloat())
                    : new MatchNews(matchId, tick, kind, faction, ticks);
        }

        @Override
        public int maxSize() {
            return 5 + 10 + 1 + 5 + 10 + 8;
        }
    };
}
