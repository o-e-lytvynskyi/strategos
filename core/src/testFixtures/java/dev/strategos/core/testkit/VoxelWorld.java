package dev.strategos.core.testkit;

import dev.strategos.core.sim.nav.BlockClass;
import dev.strategos.core.sim.nav.MobilityClass;
import dev.strategos.core.sim.nav.NavOverlay;
import dev.strategos.core.sim.nav.PageBuilder;
import dev.strategos.core.sim.nav.PageCoord;
import dev.strategos.core.sim.nav.SurfaceGrid;
import dev.strategos.core.sim.nav.SurfacePage;
import dev.strategos.core.sim.nav.VoxelSlice;
import java.util.ArrayList;
import java.util.List;

/**
 * A mutable grid of block classes for L1 terrain: {@code sizeX x height x sizeZ} voxels, match-local, air until
 * filled. It cuts {@link VoxelSlice}s per page and builds whole {@link SurfaceGrid}s, as captures and page jobs do.
 */
public final class VoxelWorld {
    private final int sizeX;
    private final int height;
    private final int sizeZ;
    private final short[] voxels;

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
        return new VoxelSlice(height, out);
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
