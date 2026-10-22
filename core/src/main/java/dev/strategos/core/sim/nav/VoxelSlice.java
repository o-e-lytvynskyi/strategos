package dev.strategos.core.sim.nav;

import dev.strategos.core.protocol.WireException;
import dev.strategos.core.protocol.WireReader;
import dev.strategos.core.protocol.WireWriter;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.Hasher;
import java.util.Arrays;

/**
 * The block classes of one page's columns over the match's Y band: 16 x 16 columns of {@code height} voxels, stored x
 * then z then y. Immutable. Its canonical form, for the journal and the hash, is run-length per column.
 */
@CoreApi
public final class VoxelSlice {
    public static final int SIDE = 16;
    public static final int MAX_HEIGHT = 128;

    private final int height;
    private final short[] voxels;
    private final long hash;

    /** {@code voxels} is copied; {@code POSITION_DEPENDENT} is cleared from every class. */
    public VoxelSlice(int height, short[] voxels) {
        if (height < 1 || height > MAX_HEIGHT) {
            throw new IllegalArgumentException("slice height " + height + " outside 1.." + MAX_HEIGHT);
        }
        if (voxels.length != SIDE * SIDE * height) {
            throw new IllegalArgumentException(voxels.length + " voxels for a slice of height " + height);
        }
        this.height = height;
        this.voxels = new short[voxels.length];
        for (int i = 0; i < voxels.length; i++) {
            this.voxels[i] = BlockClass.forSlice(voxels[i]);
        }
        this.hash = computeHash();
    }

    public int height() {
        return height;
    }

    /** The class at column (x, z), 0–15 each, and band level y, 0 to height - 1. */
    public short get(int x, int y, int z) {
        return voxels[index(x, y, z, height)];
    }

    static int index(int x, int y, int z, int height) {
        return (x * SIDE + z) * height + y;
    }

    /** Per column: the number of runs, then each run's class and length, as varints. */
    public byte[] encode() {
        WireWriter out = new WireWriter(1_024);
        out.writeVarInt(height);
        for (int column = 0; column < SIDE * SIDE; column++) {
            int base = column * height;
            int runs = 1;
            for (int y = 1; y < height; y++) {
                if (voxels[base + y] != voxels[base + y - 1]) {
                    runs++;
                }
            }
            out.writeVarInt(runs);
            int y = 0;
            while (y < height) {
                short c = voxels[base + y];
                int length = 1;
                while (y + length < height && voxels[base + y + length] == c) {
                    length++;
                }
                out.writeVarInt(c & 0xFFFF);
                out.writeVarInt(length);
                y += length;
            }
        }
        return out.toByteArray();
    }

    /** Reads {@link #encode()}'s form; anything malformed throws {@link WireException}. */
    public static VoxelSlice decode(byte[] bytes) {
        WireReader in = new WireReader(bytes);
        int height = in.readVarInt(MAX_HEIGHT);
        if (height < 1) {
            throw new WireException("slice height 0");
        }
        short[] voxels = new short[SIDE * SIDE * height];
        for (int column = 0; column < SIDE * SIDE; column++) {
            int runs = in.readVarInt(height);
            int y = 0;
            for (int r = 0; r < runs; r++) {
                int c = in.readVarInt(0xFFFF);
                int length = in.readVarInt(height - y);
                if (length < 1) {
                    throw new WireException("an empty run in column " + column);
                }
                Arrays.fill(voxels, column * height + y, column * height + y + length, (short) c);
                y += length;
            }
            if (y != height) {
                throw new WireException("column " + column + " holds " + y + " of " + height + " voxels");
            }
        }
        in.expectEnd();
        return new VoxelSlice(height, voxels);
    }

    /** Equal slices have equal hashes. */
    public long hash() {
        return hash;
    }

    private long computeHash() {
        long h = Hasher.start(0x5_1ACE);
        h = Hasher.mix(h, height);
        for (short v : voxels) {
            h = Hasher.mix(h, v);
        }
        return Hasher.finish(h);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof VoxelSlice other && other.height == height && Arrays.equals(other.voxels, voxels);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(hash);
    }
}
