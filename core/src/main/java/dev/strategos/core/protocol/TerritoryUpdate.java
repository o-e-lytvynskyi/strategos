package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;
import java.util.Arrays;

/**
 * The {@code territory} payload (TERR-01, M9 spec §2.4): one part of a territory version. A full version (at join and
 * after a restore) carries the grid as runs of equal tiles; a delta carries the tiles that changed since the previous
 * version, each as (gap, value). A version too large for one payload is split into parts sent on consecutive ticks;
 * a client applies a version once its last part arrived. Territory is public (spec §1.6 row 9).
 *
 * <p>Wire: matchId (varint), tick (varlong), version (varint), tilesX (varint ≤ 512), tilesZ (varint ≤ 512), full
 * (byte), part (byte), last (byte), start (varint: the first tile index this part covers), count (varint), then per
 * entry: full — run length (varint), value (byte); delta — gap from the previous index (varint), value (byte).
 */
@CoreApi
public final class TerritoryUpdate {
    /** The cap of one payload, below TERR-01's 8 KB. */
    public static final int CAP = 8_192;
    /** Where the encoder starts a new part: room under {@link #CAP} for the header and the last entry. */
    public static final int SPLIT_AT = 7_168;

    static final int MAX_TILES = 512;

    private final int matchId;
    private final long tick;
    private final int version;
    private final int tilesX;
    private final int tilesZ;
    private final boolean full;
    private final int part;
    private final boolean last;
    private final int start;
    private final int[] keys;
    private final byte[] values;

    /**
     * {@code keys} are run lengths for a full part and absolute tile indices for a delta part; {@code values} the
     * tiles' values ({@code TerritoryGrid.value}).
     */
    public TerritoryUpdate(
            int matchId,
            long tick,
            int version,
            int tilesX,
            int tilesZ,
            boolean full,
            int part,
            boolean last,
            int start,
            int[] keys,
            byte[] values) {
        if (keys.length != values.length) {
            throw new IllegalArgumentException("keys and values differ in length");
        }
        this.matchId = matchId;
        this.tick = tick;
        this.version = version;
        this.tilesX = tilesX;
        this.tilesZ = tilesZ;
        this.full = full;
        this.part = part;
        this.last = last;
        this.start = start;
        this.keys = keys.clone();
        this.values = values.clone();
    }

    public int matchId() {
        return matchId;
    }

    public long tick() {
        return tick;
    }

    public int version() {
        return version;
    }

    public int tilesX() {
        return tilesX;
    }

    public int tilesZ() {
        return tilesZ;
    }

    public boolean full() {
        return full;
    }

    public int part() {
        return part;
    }

    public boolean last() {
        return last;
    }

    public int start() {
        return start;
    }

    public int entries() {
        return keys.length;
    }

    public int key(int i) {
        return keys[i];
    }

    public byte value(int i) {
        return values[i];
    }

    /** The bytes this part takes on the wire. */
    public int wireSize() {
        WireWriter w = new WireWriter();
        WIRE.write(w, this);
        return w.size();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TerritoryUpdate u
                && u.matchId == matchId
                && u.tick == tick
                && u.version == version
                && u.tilesX == tilesX
                && u.tilesZ == tilesZ
                && u.full == full
                && u.part == part
                && u.last == last
                && u.start == start
                && Arrays.equals(u.keys, keys)
                && Arrays.equals(u.values, values);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * version + part) + Arrays.hashCode(keys);
    }

    @Override
    public String toString() {
        return "TerritoryUpdate[v" + version + " part " + part + (last ? " last" : "") + (full ? " full" : " delta")
                + ", " + keys.length + " entries]";
    }

    public static final WireCodec<TerritoryUpdate> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, TerritoryUpdate u) {
            out.writeVarInt(u.matchId).writeVarLong(u.tick).writeVarInt(u.version);
            out.writeVarInt(u.tilesX).writeVarInt(u.tilesZ);
            out.writeBoolean(u.full).writeByte(u.part).writeBoolean(u.last);
            out.writeVarInt(u.start).writeVarInt(u.keys.length);
            int prev = u.start - 1;
            for (int i = 0; i < u.keys.length; i++) {
                if (u.full) {
                    out.writeVarInt(u.keys[i]);
                } else {
                    out.writeVarInt(u.keys[i] - prev - 1);
                    prev = u.keys[i];
                }
                out.writeByte(u.values[i]);
            }
        }

        @Override
        public TerritoryUpdate read(WireReader in) {
            int matchId = in.readVarInt();
            long tick = in.readVarLong();
            int version = in.readVarInt();
            int tilesX = in.readVarInt(MAX_TILES);
            int tilesZ = in.readVarInt(MAX_TILES);
            boolean full = in.readBoolean();
            int part = in.readUnsignedByte();
            boolean last = in.readBoolean();
            int tiles = tilesX * tilesZ;
            int start = in.readVarInt(tiles);
            int n = in.readVarInt(tiles);
            int[] keys = new int[n];
            byte[] values = new byte[n];
            int prev = start - 1;
            for (int i = 0; i < n; i++) {
                if (full) {
                    keys[i] = in.readVarInt(tiles);
                } else {
                    prev = prev + 1 + in.readVarInt(tiles);
                    if (prev >= tiles) {
                        throw new IllegalArgumentException("a tile index past the grid: " + prev);
                    }
                    keys[i] = prev;
                }
                values[i] = (byte) in.readByte();
            }
            return new TerritoryUpdate(matchId, tick, version, tilesX, tilesZ, full, part, last, start, keys, values);
        }

        @Override
        public int maxSize() {
            return CAP;
        }
    };
}
