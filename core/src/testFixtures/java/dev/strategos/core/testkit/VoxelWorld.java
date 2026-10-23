package dev.strategos.core.testkit;

import dev.strategos.core.sim.nav.BlockClass;
import dev.strategos.core.sim.nav.Material;
import dev.strategos.core.sim.nav.MobilityClass;
import dev.strategos.core.sim.nav.NavOverlay;
import dev.strategos.core.sim.nav.PageBuilder;
import dev.strategos.core.sim.nav.PageCoord;
import dev.strategos.core.sim.nav.SliceMaterials;
import dev.strategos.core.sim.nav.SurfaceGrid;
import dev.strategos.core.sim.nav.SurfacePage;
import dev.strategos.core.sim.nav.VoxelSlice;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * A mutable grid of block classes for L1 terrain: {@code sizeX x height x sizeZ} voxels, match-local, air until
 * filled. It cuts {@link VoxelSlice}s per page and builds whole {@link SurfaceGrid}s, as captures and page jobs do.
 */
public final class VoxelWorld {
    private final int sizeX;
    private final int height;
    private final int sizeZ;
    private final short[] voxels;
    private final TreeMap<Long, Material> materials = new TreeMap<>(); // by x, z, y

    public VoxelWorld(int sizeX, int height, int sizeZ) {
        if (sizeX < 1 || sizeZ < 1 || height < 1 || height > VoxelSlice.MAX_HEIGHT) {
            throw new IllegalArgumentException("world " + sizeX + " x " + height + " x " + sizeZ);
        }
        this.sizeX = sizeX;
        this.height = height;
        this.sizeZ = sizeZ;
        this.voxels = new short[sizeX * height * sizeZ];
        java.util.Arrays.fill(voxels, BlockClass.AIR);
    }

    public int sizeX() {
        return sizeX;
    }

    public int height() {
        return height;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public short get(int x, int y, int z) {
        if (x < 0 || z < 0 || x >= sizeX || z >= sizeZ || y < 0 || y >= height) {
            return BlockClass.AIR;
        }
        return voxels[(x * sizeZ + z) * height + y];
    }

    public VoxelWorld set(int x, int y, int z, short c) {
        voxels[(x * sizeZ + z) * height + y] = c;
        return this;
    }

    /** The block at (x, y, z) has a {@code resource_yield} entry (ECO-02); null clears it. */
    public VoxelWorld material(int x, int y, int z, @Nullable Material m) {
        long key = ((long) x << 32) | ((long) z << 12) | y;
        if (m == null) {
            materials.remove(key);
        } else {
            materials.put(key, m);
        }
        return this;
    }

    /** The material at (x, y, z), or null. */
    public @Nullable Material materialAt(int x, int y, int z) {
        return materials.get(((long) x << 32) | ((long) z << 12) | y);
    }

    /** Fills the box between two corners, both inclusive. */
    public VoxelWorld fill(int x0, int y0, int z0, int x1, int y1, int z1, short c) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    set(x, y, z, c);
                }
            }
        }
        return this;
    }

    /** The page's slice; columns outside the world are air. */
    public VoxelSlice slice(PageCoord page) {
        short[] out = new short[VoxelSlice.SIDE * VoxelSlice.SIDE * height];
        for (int x = 0; x < VoxelSlice.SIDE; x++) {
            for (int z = 0; z < VoxelSlice.SIDE; z++) {
                for (int y = 0; y < height; y++) {
                    out[(x * VoxelSlice.SIDE + z) * height + y] = get(page.x() * 16 + x, y, page.z() * 16 + z);
                }
            }
        }
        return new VoxelSlice(height, out, materials(page));
    }

    /** The page's materials, as a capture finds them. */
    private SliceMaterials materials(PageCoord page) {
        List<long[]> found = new ArrayList<>(); // slice index, then the key
        for (Map.Entry<Long, Material> e : materials.entrySet()) {
            int x = (int) (e.getKey() >>> 32);
            int z = (int) ((e.getKey() >>> 12) & 0xFFFFF);
            int y = (int) (e.getKey() & 0xFFF);
            if (x >> 4 == page.x() && z >> 4 == page.z() && y < height) {
                found.add(new long[] {VoxelSlice.index(x & 15, y, z & 15, height), e.getKey()});
            }
        }
        found.sort((a, b) -> Long.compare(a[0], b[0]));
        List<Material> legend = new ArrayList<>();
        int[] voxels = new int[found.size()];
        int[] entries = new int[found.size()];
        for (int k = 0; k < found.size(); k++) {
            Material m = materials.get(found.get(k)[1]);
            int entry = legend.indexOf(m);
            if (entry < 0) {
                entry = legend.size();
                legend.add(m);
            }
            voxels[k] = (int) found.get(k)[0];
            entries[k] = entry;
        }
        return new SliceMaterials(legend, voxels, entries);
    }

    /** Every page built and published, as a match's prewarm does. */
    public SurfaceGrid grid(MobilityClass mobility, NavOverlay overlay) {
        SurfaceGrid grid = SurfaceGrid.empty(sizeX, sizeZ, mobility);
        PageBuilder builder = new PageBuilder(mobility);
        List<SurfacePage> pages = new ArrayList<>();
        for (int pz = 0; pz < grid.pagesZ(); pz++) {
            for (int px = 0; px < grid.pagesX(); px++) {
                PageCoord page = new PageCoord(px, pz);
                pages.add(builder.build(slice(page), overlay, page));
            }
        }
        return grid.publishAll(pages);
    }

    public SurfaceGrid grid() {
        return grid(MobilityClass.INFANTRY, NavOverlay.EMPTY);
    }
}
