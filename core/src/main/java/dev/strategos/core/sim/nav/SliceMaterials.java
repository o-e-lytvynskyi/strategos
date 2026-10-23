package dev.strategos.core.sim.nav;

import dev.strategos.core.protocol.WireException;
import dev.strategos.core.protocol.WireReader;
import dev.strategos.core.protocol.WireWriter;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.Hasher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The economy's view of one slice (ECO-02, M3 spec §1.6 row 1): the voxels whose block has a {@code resource_yield}
 * entry, in ascending index order, each with its entry in a legend of distinct materials. Immutable. It travels inside
 * the slice's encoding, after the classes, only when it holds anything, so a slice without materials encodes and
 * hashes as before.
 */
@CoreApi
public final class SliceMaterials {
    /** The most legend entries and material voxels a slice may hold. */
    public static final int MAX_LEGEND = 64;

    private static final int MAX_STRING = 64;

    private final List<Material> legend;
    private final int[] voxels;
    private final int[] entries;

    /** {@code voxels} ascending and distinct, {@code entries[k]} the legend index of voxel {@code k}; both copied. */
    public SliceMaterials(List<Material> legend, int[] voxels, int[] entries) {
        if (legend.size() > MAX_LEGEND || voxels.length != entries.length) {
            throw new IllegalArgumentException(
                    legend.size() + " legend entries, " + voxels.length + " voxels, " + entries.length + " entries");
        }
        for (int k = 0; k < voxels.length; k++) {
            if ((k > 0 && voxels[k] <= voxels[k - 1]) || entries[k] < 0 || entries[k] >= legend.size()) {
                throw new IllegalArgumentException("material voxel " + k + " out of order or of an unknown entry");
            }
        }
        this.legend = List.copyOf(legend);
        this.voxels = voxels.clone();
        this.entries = entries.clone();
    }

    /** No materials. */
    public static SliceMaterials none() {
        return new SliceMaterials(List.of(), new int[0], new int[0]);
    }

    public boolean isEmpty() {
        return voxels.length == 0;
    }

    public int size() {
        return voxels.length;
    }

    /** The {@code k}th material voxel's index in the slice ({@link VoxelSlice#index}). */
    public int voxel(int k) {
        return voxels[k];
    }

    public Material material(int k) {
        return legend.get(entries[k]);
    }

    /** The material at slice index {@code voxel}, or null. */
    public @Nullable Material at(int voxel) {
        int k = Arrays.binarySearch(voxels, voxel);
        return k >= 0 ? legend.get(entries[k]) : null;
    }

    public List<Material> legend() {
        return legend;
    }

    void write(WireWriter out) {
        out.writeVarInt(legend.size());
        for (Material m : legend) {
            out.writeByte(m.kind().code());
            out.writeString(m.resource(), MAX_STRING);
            out.writeString(m.subtype(), MAX_STRING);
            out.writeVarLong(m.amount());
        }
        out.writeVarInt(voxels.length);
        int last = -1;
        for (int k = 0; k < voxels.length; k++) {
            out.writeVarInt(voxels[k] - last - 1);
            out.writeVarInt(entries[k]);
            last = voxels[k];
        }
    }

    static SliceMaterials read(WireReader in, int slots) {
        int n = in.readVarInt(MAX_LEGEND);
        List<Material> legend = new ArrayList<>(n);
        for (int k = 0; k < n; k++) {
            int kind = in.readUnsignedByte();
            MaterialKind materialKind;
            try {
                materialKind = MaterialKind.ofCode(kind);
            } catch (IllegalArgumentException e) {
                throw new WireException("unknown material kind " + kind);
            }
            legend.add(
                    new Material(materialKind, in.readString(MAX_STRING), in.readString(MAX_STRING), in.readVarLong()));
        }
        int count = in.readVarInt(slots);
        int[] voxels = new int[count];
        int[] entries = new int[count];
        int last = -1;
        for (int k = 0; k < count; k++) {
            voxels[k] = last + 1 + in.readVarInt(slots);
            entries[k] = in.readVarInt(Math.max(0, n - 1));
            if (voxels[k] >= slots) {
                throw new WireException("material voxel " + voxels[k] + " outside a slice of " + slots);
            }
            last = voxels[k];
        }
        return new SliceMaterials(legend, voxels, entries);
    }

    long mix(long h) {
        long x = Hasher.mix(h, voxels.length);
        for (Material m : legend) {
            x = Hasher.mix(x, m.kind().code());
            x = Hasher.mix(x, m.resource().hashCode());
            x = Hasher.mix(x, m.subtype().hashCode());
            x = Hasher.mix(x, m.amount());
        }
        for (int k = 0; k < voxels.length; k++) {
            x = Hasher.mix(x, voxels[k]);
            x = Hasher.mix(x, entries[k]);
        }
        return x;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SliceMaterials other
                && other.legend.equals(legend)
                && Arrays.equals(other.voxels, voxels)
                && Arrays.equals(other.entries, entries);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(voxels) * 31 + legend.hashCode();
    }
}
