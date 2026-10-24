package dev.strategos.core.sim;

import dev.strategos.core.sim.econ.NodeStore;
import dev.strategos.core.sim.nav.BlockClass;
import dev.strategos.core.sim.nav.Material;
import dev.strategos.core.sim.nav.MaterialKind;
import dev.strategos.core.sim.nav.SliceMaterials;
import dev.strategos.core.sim.nav.TerrainEdits;
import dev.strategos.core.sim.nav.VoxelSlice;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The materials of a match's terrain as the core now sees it: each page's last slice, less the voxels the core has
 * edited since (ECO-03). Split out of {@link MatchState} in M4 T1; it shares the state's slices and edits.
 */
final class TerrainMaterials {
    private final MatchBounds bounds;
    private final int pagesX;
    private final int pagesZ;
    private final byte[][] sliceBytes;
    private final TerrainEdits terrainEdits;

    TerrainMaterials(MatchBounds bounds, int pagesX, int pagesZ, byte[][] sliceBytes, TerrainEdits terrainEdits) {
        this.bounds = bounds;
        this.pagesX = pagesX;
        this.pagesZ = pagesZ;
        this.sliceBytes = sliceBytes;
        this.terrainEdits = terrainEdits;
    }

    /**
     * The materials in the box (x0..x1, y0..y1, z0..z1), match-local x and z, absolute y, as the core now sees its
     * terrain: each page's last slice, less every voxel the core has edited since (ECO-03). Positions are packed by
     * {@link NodeStore#pack}; the result is in position order.
     */
    List<long[]> in(int x0, int y0, int z0, int x1, int y1, int z1, MaterialKind kind) {
        List<long[]> out = new ArrayList<>(); // packed position, material amount
        MatchBounds b = bounds;
        for (int pz = Math.max(0, z0 >> 4); pz <= Math.min(pagesZ - 1, z1 >> 4); pz++) {
            for (int px = Math.max(0, x0 >> 4); px <= Math.min(pagesX - 1, x1 >> 4); px++) {
                int page = pz * pagesX + px;
                if (sliceBytes[page] == null) {
                    continue;
                }
                VoxelSlice slice = VoxelSlice.decode(sliceBytes[page]);
                SliceMaterials m = slice.materials();
                int height = slice.height();
                for (int k = 0; k < m.size(); k++) {
                    if (m.material(k).kind() != kind) {
                        continue;
                    }
                    int column = m.voxel(k) / height;
                    int band = m.voxel(k) % height;
                    int x = px * 16 + column / 16;
                    int z = pz * 16 + column % 16;
                    int y = b.minY() + band;
                    if (x < x0 || x > x1 || z < z0 || z > z1 || y < y0 || y > y1) {
                        continue;
                    }
                    int edited = terrainEdits.classAt(page, TerrainEdits.cell(column / 16, band, column % 16));
                    if (edited >= 0 && BlockClass.is((short) edited, BlockClass.PASSABLE)) {
                        continue; // the core took it away
                    }
                    out.add(new long[] {NodeStore.pack(x, y, z), m.material(k).amount()});
                }
            }
        }
        out.sort((a, c) -> Long.compare(a[0], c[0]));
        return out;
    }

    /**
     * The material at match-local (x, z) and absolute height y as the core now sees its terrain, or null: none, or one
     * the core has taken away.
     */
    @Nullable Material at(int x, int y, int z) {
        MatchBounds b = bounds;
        if (x < 0 || z < 0 || x >= b.sizeX() || z >= b.sizeZ()) {
            return null;
        }
        int page = (z >> 4) * pagesX + (x >> 4);
        if (sliceBytes[page] == null) {
            return null;
        }
        VoxelSlice slice = VoxelSlice.decode(sliceBytes[page]);
        int band = y - b.minY();
        if (band < 0 || band >= slice.height()) {
            return null;
        }
        int edited = terrainEdits.classAt(page, TerrainEdits.cell(x & 15, band, z & 15));
        if (edited >= 0 && BlockClass.is((short) edited, BlockClass.PASSABLE)) {
            return null;
        }
        return slice.materials().at(((x & 15) * 16 + (z & 15)) * slice.height() + band);
    }
}
