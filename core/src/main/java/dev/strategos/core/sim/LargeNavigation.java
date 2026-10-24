package dev.strategos.core.sim;

import dev.strategos.core.sim.nav.FootprintOverlay;
import dev.strategos.core.sim.nav.MobilityClass;
import dev.strategos.core.sim.nav.PageBuilder;
import dev.strategos.core.sim.nav.PageCoord;
import dev.strategos.core.sim.nav.SurfaceGrid;
import dev.strategos.core.sim.nav.SurfacePage;
import dev.strategos.core.sim.nav.TerrainEdits;
import dev.strategos.core.sim.nav.VoxelSlice;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** PATH-11's second derived grid; a changed border also rebuilds its neighbouring page's footprint clearance. */
final class LargeNavigation {
    private SurfaceGrid grid;
    private final PageBuilder builder = new PageBuilder(MobilityClass.LARGE);
    private final BitSet changed = new BitSet();
    private final BitSet known = new BitSet();

    LargeNavigation(MatchBounds bounds) {
        grid = SurfaceGrid.empty(bounds.sizeX(), bounds.sizeZ(), MobilityClass.LARGE);
    }

    SurfaceGrid grid() {
        return grid;
    }

    void beginTick() {
        changed.clear();
    }

    int changedCount() {
        return changed.cardinality();
    }

    int changedPage(int k) {
        int page = changed.nextSetBit(0);
        for (int i = 0; i < k && page >= 0; i++) page = changed.nextSetBit(page + 1);
        if (page < 0) throw new IndexOutOfBoundsException("large changed page " + k);
        return page;
    }

    void publish(
            SurfaceGrid infantry,
            List<SurfacePage> pages,
            byte[][] slices,
            TerrainEdits edits,
            FootprintOverlay footprints) {
        BitSet dirty = new BitSet(slices.length);
        for (SurfacePage page : pages) {
            int x = page.coord().x();
            int z = page.coord().z();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) mark(dirty, x + dx, z + dz);
            }
        }
        // Decode only the dirty pages and their halo, once for this publication. No second persistent voxel copy.
        @Nullable VoxelSlice[] decoded = new VoxelSlice[slices.length];
        PageBuilder.Columns columns = (x, z, floor, clearance) -> {
            if (!infantry.inside(x, z) || footprints.blocked(x, z)) return -1;
            int index = (z >> 4) * grid.pagesX() + (x >> 4);
            VoxelSlice slice = slice(index, slices, edits, decoded);
            return slice == null ? -1 : PageBuilder.supportingFloor(slice, x & 15, z & 15, floor, clearance);
        };
        List<SurfacePage> built = new ArrayList<>();
        for (int index = dirty.nextSetBit(0); index >= 0; index = dirty.nextSetBit(index + 1)) {
            VoxelSlice slice = slice(index, slices, edits, decoded);
            if (slice == null) continue;
            PageCoord coord = new PageCoord(index % grid.pagesX(), index / grid.pagesX());
            SurfacePage page = builder.build(slice, footprints.of(coord), coord, columns);
            if (!known.get(index)
                    || page.hash() != grid.page(coord.x(), coord.z()).hash()) {
                built.add(page);
                changed.set(index);
            }
            known.set(index);
        }
        if (!built.isEmpty()) grid = grid.publishAll(built);
    }

    private void mark(BitSet dirty, int x, int z) {
        if (x >= 0 && z >= 0 && x < grid.pagesX() && z < grid.pagesZ()) dirty.set(z * grid.pagesX() + x);
    }

    private static @Nullable VoxelSlice slice(
            int index, byte[][] slices, TerrainEdits edits, @Nullable VoxelSlice[] decoded) {
        if (slices[index] == null) return null;
        VoxelSlice cached = decoded[index];
        if (cached == null) {
            cached = edits.applyTo(index, VoxelSlice.decode(slices[index]));
            decoded[index] = cached;
        }
        return cached;
    }
}
