package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The {@code unit_summary} payload (NET-08, 04 §12.4 tier 2): every unit a faction knows in match {@code matchId} at
 * {@code tick}, as a full frame, twice a second. A unit is keyed by its index, which only one live unit holds at a time
 * and every body carries; its template, state and match-local column quantised to an eighth of a block. On the wire
 * the entries go by faction, in index order, about 6 bytes each: the index's step, template and state in one byte, and
 * X and Z in two each for spaces up to 2,048 blocks.
 */
@CoreApi
public record UnitSummary(int matchId, long tick, List<Entry> units) {
    /** Entries a frame may hold. */
    public static final int MAX_UNITS = 2_048;

    private static final int TEMPLATE_ESCAPE = 31;
    private static final int MAX_FACTIONS = 16;

    /** One unit: {@code qx} and {@code qz} are match-local X and Z in eighths of a block. */
    @CoreApi
    public record Entry(int index, int faction, int template, int state, int qx, int qz) {
        public Entry {
            if (index < 0
                    || faction < 0
                    || faction >= MAX_FACTIONS
                    || template < 0
                    || state < 0
                    || state > 7
                    || qx < 0
                    || qz < 0) {
                throw new IllegalArgumentException("a summary entry out of range: " + index + ", " + faction + ", "
                        + template + ", " + state + ", " + qx + ", " + qz);
            }
        }

        /** An entry at match-local ({@code x}, {@code z}), rounded to the nearest eighth and kept non-negative. */
        public static Entry at(int index, int faction, int template, int state, double x, double z) {
            return new Entry(index, faction, template, state, quantise(x), quantise(z));
        }

        public double x() {
            return qx / 8.0;
        }

        public double z() {
            return qz / 8.0;
        }

        private static int quantise(double v) {
            return (int) Math.max(0, Math.round(v * 8));
        }
    }

    public UnitSummary {
        if (units.size() > MAX_UNITS) {
            throw new IllegalArgumentException(units.size() + " units, more than a frame's " + MAX_UNITS);
        }
        List<Entry> sorted = new ArrayList<>(units);
        sorted.sort(Comparator.comparingInt(Entry::faction).thenComparingInt(Entry::index));
        units = List.copyOf(sorted);
    }

    public static final WireCodec<UnitSummary> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, UnitSummary s) {
            out.writeVarInt(s.matchId()).writeVarLong(s.tick());
            List<Entry> units = s.units();
            int groups = 0;
            for (int k = 0; k < units.size(); k++) {
                if (k == 0 || units.get(k).faction() != units.get(k - 1).faction()) {
                    groups++;
                }
            }
            out.writeVarInt(groups);
            int k = 0;
            while (k < units.size()) {
                int faction = units.get(k).faction();
                int end = k;
                while (end < units.size() && units.get(end).faction() == faction) {
                    end++;
                }
                out.writeByte(faction).writeVarInt(end - k);
                int previous = -1;
                for (int j = k; j < end; j++) {
                    Entry e = units.get(j);
                    out.writeVarInt(e.index() - previous - 1);
                    previous = e.index();
                    int template = Math.min(e.template(), TEMPLATE_ESCAPE);
                    out.writeByte(template | e.state() << 5);
                    if (template == TEMPLATE_ESCAPE) {
                        out.writeVarInt(e.template());
                    }
                    out.writeVarInt(e.qx()).writeVarInt(e.qz());
                }
                k = end;
            }
        }

        @Override
        public UnitSummary read(WireReader in) {
            int matchId = in.readVarInt();
            long tick = in.readVarLong();
            int groups = in.readVarInt(MAX_FACTIONS);
            List<Entry> units = new ArrayList<>();
            for (int g = 0; g < groups; g++) {
                int faction = in.readUnsignedByte();
                int count = in.readVarInt(MAX_UNITS - units.size());
                int previous = -1;
                for (int j = 0; j < count; j++) {
                    int index = previous + 1 + in.readVarInt();
                    previous = index;
                    int packed = in.readUnsignedByte();
                    int template = packed & TEMPLATE_ESCAPE;
                    if (template == TEMPLATE_ESCAPE) {
                        template = in.readVarInt();
                    }
                    units.add(new Entry(index, faction, template, packed >>> 5, in.readVarInt(), in.readVarInt()));
                }
            }
            return new UnitSummary(matchId, tick, units);
        }

        @Override
        public int maxSize() {
            return 5 + 10 + 5 + MAX_FACTIONS * (1 + 5) + MAX_UNITS * (5 + 1 + 5 + 5 + 5);
        }
    };
}
