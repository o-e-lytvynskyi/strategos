package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.sim.nav.MobilityClass;
import dev.strategos.core.sim.nav.NavOverlay;
import dev.strategos.core.sim.nav.PageBuilder;
import dev.strategos.core.sim.nav.PageCoord;
import dev.strategos.core.sim.nav.SurfacePage;
import dev.strategos.core.sim.nav.VoxelSlice;
import java.util.Arrays;

/**
 * A rebuilt terrain page entering a match (04 §9.2, PATH-15): the page, the slice it was built from in its encoded
 * form, and the slice's hash. INGEST publishes the page; the journal keeps the slice once per hash and rebuilds the page
 * from it on replay. It lives beside {@link InputEvent}, since a sealed type outside a named module permits only its own
 * package.
 */
@CoreApi
public final class TerrainPage implements InputEvent {
    private final SurfacePage page;
    private final byte[] slice;
    private final long sliceHash;

    public TerrainPage(SurfacePage page, byte[] slice, long sliceHash) {
        this.page = page;
        this.slice = slice.clone();
        this.sliceHash = sliceHash;
    }

    /** The page built from {@code slice}, as a page job does. */
    public static TerrainPage build(PageCoord coord, VoxelSlice slice, PageBuilder builder) {
        return new TerrainPage(builder.build(slice, NavOverlay.EMPTY, coord), slice.encode(), slice.hash());
    }

    /** The page rebuilt from an encoded slice, as a replay does: infantry, no overlay. */
    public static TerrainPage rebuild(PageCoord coord, byte[] slice) {
        VoxelSlice decoded = VoxelSlice.decode(slice);
        return build(coord, decoded, new PageBuilder(MobilityClass.INFANTRY));
    }

    public SurfacePage page() {
        return page;
    }

    public PageCoord coord() {
        return page.coord();
    }

    /** The slice's run-length form (a copy). */
    public byte[] slice() {
        return slice.clone();
    }

    public long sliceHash() {
        return sliceHash;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TerrainPage other
                && other.sliceHash == sliceHash
                && other.page.equals(page)
                && Arrays.equals(other.slice, slice);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(sliceHash) * 31 + page.coord().hashCode();
    }

    @Override
    public String toString() {
        return "TerrainPage[" + page.coord() + ", slice " + Long.toHexString(sliceHash) + "]";
    }
}
