package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The {@code unit_hud} payload (NET-08, CMB-06): the units of match {@code matchId} whose hit points changed for this
 * player since the last one, each as its index and its hit points' fraction in 255ths; a unit never listed is whole.
 * At most five a second.
 */
@CoreApi
public record UnitHud(int matchId, long tick, List<Entry> units) {
    public static final int MAX_UNITS = 2_048;
    public static final int WHOLE = 255;

    /** A unit's index and its hit points as a fraction of its most, 0 to 255. */
    @CoreApi
    public record Entry(int index, int health) {
        public Entry {
            if (index < 0 || health < 0 || health > WHOLE) {
                throw new IllegalArgumentException("a hud entry out of range: " + index + ", " + health);
            }
        }

        /** The fraction {@code hp} of {@code max} in 255ths, rounded; a unit still alive never shows 0. */
        public static int health(double hp, double max) {
            if (!(max > 0) || !(hp > 0)) {
                return 0;
            }
            return (int) Math.max(1, Math.min(WHOLE, Math.round(hp / max * WHOLE)));
        }
    }

    public UnitHud {
        if (units.size() > MAX_UNITS) {
            throw new IllegalArgumentException(units.size() + " units, more than " + MAX_UNITS);
        }
        List<Entry> sorted = new ArrayList<>(units);
        sorted.sort(Comparator.comparingInt(Entry::index));
        units = List.copyOf(sorted);
    }

    public static final WireCodec<UnitHud> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, UnitHud h) {
            out.writeVarInt(h.matchId())
                    .writeVarLong(h.tick())
                    .writeVarInt(h.units().size());
            int previous = -1;
            for (Entry e : h.units()) {
                out.writeVarInt(e.index() - previous - 1).writeByte(e.health());
                previous = e.index();
            }
        }

        @Override
        public UnitHud read(WireReader in) {
            int matchId = in.readVarInt();
            long tick = in.readVarLong();
            int count = in.readVarInt(MAX_UNITS);
            List<Entry> units = new ArrayList<>(count);
            int previous = -1;
            for (int k = 0; k < count; k++) {
                int index = previous + 1 + in.readVarInt();
                previous = index;
                units.add(new Entry(index, in.readUnsignedByte()));
            }
            return new UnitHud(matchId, tick, units);
        }

        @Override
        public int maxSize() {
            return 5 + 10 + 5 + MAX_UNITS * (5 + 1);
        }
    };
}
