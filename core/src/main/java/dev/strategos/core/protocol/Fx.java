package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;
import java.util.List;

/**
 * The {@code fx} payload (AUD-01, 04 §12.1): events of match {@code matchId} for a client to show or sound, each where
 * it happened in eighths of a block from the match space's origin. It goes only to an event's audience: the players
 * who track the unit, or those whose faction sees the cell; the server never plays a sound itself.
 */
@CoreApi
public record Fx(int matchId, List<Event> events) {
    public static final int MAX_EVENTS = 64;
    /** The farthest an event's X or Z may be from the origin, in eighths: 8,191 blocks. */
    public static final int MAX_EIGHTHS = 65_535;

    /** One event: its kind and where, X and Z within the space and Y from the origin's height, in eighths. */
    @CoreApi
    public record Event(FxKind kind, int x8, int y8, int z8) {
        public Event {
            if (x8 < 0 || x8 > MAX_EIGHTHS || z8 < 0 || z8 > MAX_EIGHTHS) {
                throw new IllegalArgumentException("an fx event outside its space: " + x8 + ", " + z8);
            }
        }

        /**
         * The event of {@code kind} at match-local block coordinates, rounded to an eighth; X and Z clamped into the
         * range, since a body may stand a little past its space's edge.
         */
        public static Event at(FxKind kind, double x, double y, double z) {
            return new Event(kind, eighths(x), (int) Math.round(y * 8), eighths(z));
        }

        private static int eighths(double v) {
            return (int) Math.max(0, Math.min(MAX_EIGHTHS, Math.round(v * 8)));
        }
    }

    public Fx {
        if (events.size() > MAX_EVENTS) {
            throw new IllegalArgumentException(events.size() + " fx events, more than " + MAX_EVENTS);
        }
        events = List.copyOf(events);
    }

    public static final WireCodec<Fx> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, Fx fx) {
            out.writeVarInt(fx.matchId()).writeVarInt(fx.events().size());
            for (Event e : fx.events()) {
                WireCodecs.FX_KIND.write(out, e.kind());
                out.writeShort(e.x8()).writeZigZag(e.y8()).writeShort(e.z8());
            }
        }

        @Override
        public Fx read(WireReader in) {
            int matchId = in.readVarInt();
            int count = in.readVarInt(MAX_EVENTS);
            Event[] events = new Event[count];
            for (int k = 0; k < count; k++) {
                FxKind kind = WireCodecs.FX_KIND.read(in);
                int x8 = in.readUnsignedShort();
                int y8 = in.readZigZagInt();
                events[k] = new Event(kind, x8, y8, in.readUnsignedShort());
            }
            return new Fx(matchId, List.of(events));
        }

        @Override
        public int maxSize() {
            return 5 + 1 + MAX_EVENTS * (1 + 2 + 5 + 2); // the count, at most 64, is one byte
        }
    };
}
