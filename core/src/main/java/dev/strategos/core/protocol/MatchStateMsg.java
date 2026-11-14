package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The {@code match_state} payload: where a match is in its life, its last completed tick, and at
 * {@link Lifecycle#ENDED} the match results (M6 §2.3), and each faction's equipment tiers {@code looks} (TECH-03; empty
 * when none is known, as in a message of the form before it). On the wire the results come first, then the looks, so an
 * older reader that stops after the results still reads the rest; at {@code ENDED} without results no looks are
 * written, since the reader could not tell them from results.
 */
@CoreApi
public record MatchStateMsg(
        int matchId,
        Lifecycle lifecycle,
        long tick,
        @Nullable MatchResults results,
        List<FactionLook> looks) {
    /** The most factions a match has, which bounds {@code looks}. */
    public static final int MAX_FACTIONS = 16;

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

    public MatchStateMsg {
        looks = List.copyOf(looks);
        if (looks.size() > MAX_FACTIONS) {
            throw new IllegalArgumentException("more looks than factions: " + looks.size());
        }
    }

    public MatchStateMsg(int matchId, Lifecycle lifecycle, long tick, @Nullable MatchResults results) {
        this(matchId, lifecycle, tick, results, List.of());
    }

    public MatchStateMsg(int matchId, Lifecycle lifecycle, long tick) {
        this(matchId, lifecycle, tick, null, List.of());
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
            if (!value.looks().isEmpty() && (value.lifecycle() != Lifecycle.ENDED || value.results() != null)) {
                out.writeVarInt(value.looks().size());
                for (FactionLook look : value.looks()) {
                    out.writeByte(look.pack());
                }
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
            List<FactionLook> looks = List.of();
            if (in.remaining() > 0) {
                int count = in.readVarInt();
                if (count > MAX_FACTIONS) {
                    throw new WireException("too many looks " + count);
                }
                FactionLook[] read = new FactionLook[count];
                for (int k = 0; k < count; k++) {
                    read[k] = FactionLook.unpack(in.readUnsignedByte());
                }
                looks = List.of(read);
            }
            return new MatchStateMsg(id, lifecycle, tick, results, looks);
        }

        @Override
        public int maxSize() {
            return 5 + 1 + 10 + MatchResults.WIRE.maxSize() + 5 + MAX_FACTIONS;
        }
    };
}
