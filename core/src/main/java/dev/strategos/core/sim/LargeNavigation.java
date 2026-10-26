package dev.strategos.core.sim;

import dev.strategos.core.sim.nav.FootprintOverlay;
import dev.strategos.core.sim.nav.MobilityClass;
import dev.strategos.core.sim.nav.PageBuilder;
import dev.strategos.core.sim.nav.PageCoord;
import dev.strategos.core.sim.nav.SurfaceGrid;
import dev.strategos.core.sim.nav.SurfacePage;
import dev.strategos.core.sim.nav.TerrainEdits;
import dev.strategos.core.sim.nav.VoxelSlice;
import it.unimi.dsi.fastutil.ints.IntArrayList;
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
    private final IntArrayList changedPages = new IntArrayList();
    private final long[] borderSignatures;
    private final BitSet signatureKnown;

    LargeNavigation(MatchBounds bounds) {
        grid = SurfaceGrid.empty(bounds.sizeX(), bounds.sizeZ(), MobilityClass.LARGE);
        int pages = grid.pagesX() * grid.pagesZ();
        borderSignatures = new long[pages * 8];
        signatureKnown = new BitSet(pages);
    }

    SurfaceGrid grid() {
        return grid;
    }

    long bytes() {
        return grid.bytes() + Long.BYTES * (long) borderSignatures.length + signatureKnown.size() / Byte.SIZE;
    }

    void beginTick() {
        changed.clear();
        changedPages.clear();
    }

    int changedCount() {
        return changedPages.size();
    }

    int changedPage(int k) {
        return changedPages.getInt(k);
    }

    void publish(
            SurfaceGrid infantry,
            List<SurfacePage> pages,
            byte[][] slices,
            TerrainEdits edits,
            FootprintOverlay footprints,
            @Nullable VoxelSlice[] decoded) {
        BitSet dirty = new BitSet(slices.length);
        for (SurfacePage page : pages) {
            int x = page.coord().x();
            int z = page.coord().z();
            int index = z * grid.pagesX() + x;
            mark(dirty, x, z);
            VoxelSlice slice = slice(index, slices, edits, decoded);
            int direction = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    long signature = slice == null ? 0 : borderSignature(slice, footprints, x, z, dx, dz);
                    int at = index * 8 + direction++;
                    if (!signatureKnown.get(index) || borderSignatures[at] != signature) {
                        mark(dirty, x + dx, z + dz);
                    }
                    borderSignatures[at] = signature;
                }
            }
            signatureKnown.set(index);
        }
        // Infantry rebuilding and the halo share effective slices for this publication only.
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
        if (!built.isEmpty()) {
            grid = grid.publishAll(built);
            changedPages.clear();
            for (int page = changed.nextSetBit(0); page >= 0; page = changed.nextSetBit(page + 1)) {
                changedPages.add(page);
            }
        }
    }

    /** Full-height effective border classes: headroom and footprint changes matter even without surface changes. */
    private static long borderSignature(
            VoxelSlice slice, FootprintOverlay footprints, int pageX, int pageZ, int dx, int dz) {
        long hash = (0xcbf29ce484222325L ^ slice.height()) * 0x100000001b3L;
        int minX = dx < 0 ? 0 : dx > 0 ? 15 : 0;
        int maxX = dx == 0 ? 15 : minX;
        int minZ = dz < 0 ? 0 : dz > 0 ? 15 : 0;
        int maxZ = dz == 0 ? 15 : minZ;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                hash = (hash ^ (footprints.blocked(pageX * 16 + x, pageZ * 16 + z) ? 1 : 0)) * 0x100000001b3L;
                for (int y = 0; y < slice.height(); y++) {
                    int cls = slice.get(x, y, z) & 0xFFFF;
                    hash = (hash ^ (cls & 0xFF)) * 0x100000001b3L;
                    hash = (hash ^ (cls >>> 8)) * 0x100000001b3L;
                }
            }
        }
        return hash;
    }

    private void mark(BitSet dirty, int x, int z) {
        if (x >= 0 && z >= 0 && x < grid.pagesX() && z < grid.pagesZ()) dirty.set(z * grid.pagesX() + x);
    }

    static @Nullable VoxelSlice slice(int index, byte[][] slices, TerrainEdits edits, @Nullable VoxelSlice[] decoded) {
        if (slices[index] == null) return null;
        VoxelSlice cached = decoded[index];
        if (cached == null) {
            cached = edits.applyTo(index, VoxelSlice.decode(slices[index]));
            decoded[index] = cached;
        }
        return cached;
    }
}
