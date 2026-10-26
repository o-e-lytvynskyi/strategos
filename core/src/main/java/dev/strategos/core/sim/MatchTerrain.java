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
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Derived navigation and voxel reconciliation, owned by the match, never saved as authoritative state. */
final class MatchTerrain {
    private final MatchState state;
    private final LargeNavigation large;
    private SurfaceGrid surfaces;
    private final long[] pageSlices;
    private final BitSet published;
    /** Pages whose slice is known: published, or named by a restored snapshot whose slice was missing. */
    private final BitSet known;

    private final byte[][] sliceBytes; // each published page's last captured slice, for the core's own rebuilds
    private final TerrainEdits terrainEdits;
    private final FootprintOverlay footprints;
    private final BitSet rebuild;
    private final TerrainMaterials materials;
    private final PageBuilder rebuilder = new PageBuilder(MobilityClass.INFANTRY);
    private int[] changed = new int[4];
    private int changedCount;

    MatchTerrain(MatchState state) {
        this.state = state;
        this.surfaces = SurfaceGrid.empty(
                state.config().bounds().sizeX(), state.config().bounds().sizeZ(), MobilityClass.INFANTRY);
        this.pageSlices = new long[surfaces.pagesX() * surfaces.pagesZ()];
        this.published = new BitSet(pageSlices.length);
        this.known = new BitSet(pageSlices.length);
        this.sliceBytes = new byte[pageSlices.length][];
        this.terrainEdits = new TerrainEdits(pageSlices.length);
        this.footprints = new FootprintOverlay(surfaces.pagesX(), surfaces.pagesZ());
        this.rebuild = new BitSet(pageSlices.length);
        this.materials = new TerrainMaterials(
                state.config().bounds(), surfaces.pagesX(), surfaces.pagesZ(), sliceBytes, terrainEdits);
        this.large = new LargeNavigation(state.config().bounds());
    }

    void beginTick() {
        changedCount = 0;
        large.beginTick();
    }

    TerrainMaterials materials() {
        return materials;
    }

    SurfaceGrid surfaces(MobilityClass mobility) {
        return mobility == MobilityClass.INFANTRY ? surfaces : large.grid();
    }

    long navigationBytes() {
        return surfaces.bytes() + large.bytes();
    }

    boolean hasFootprints() {
        return footprints.any();
    }

    int changedPageCount(MobilityClass mobility) {
        return mobility == MobilityClass.INFANTRY ? changedCount : large.changedCount();
    }

    int changedPage(int k, MobilityClass mobility) {
        return mobility == MobilityClass.INFANTRY ? changedPage(k) : large.changedPage(k);
    }

    /** The published terrain: a copy-on-write grid, so a job keeps the one it captured. */
    public SurfaceGrid surfaces() {
        return surfaces;
    }

    /**
     * Publishes a rebuilt page; a page outside the match's grid is refused. A page whose slice differs from the one
     * last known there counts as changed this tick: routes across it are asked for again (PATH-15). The core's own
     * edits and footprints are laid over the slice (M3 spec §1.6 row 5): those up to the capture's sample tick are in
     * it already and are forgotten, the rest are applied by rebuilding the page here.
     */
    public void publish(TerrainPage page) {
        publishAll(List.of(page));
    }

    /** Publishes a tick's rebuilt pages at once, in their order, so the grid's components are joined once. */
    public void publishAll(List<TerrainPage> pages) {
        if (pages.isEmpty()) {
            return;
        }
        List<SurfacePage> built = new ArrayList<>(pages.size());
        @Nullable VoxelSlice[] decoded = new VoxelSlice[sliceBytes.length];
        for (TerrainPage page : pages) {
            int index = pageIndex(page.coord());
            boolean changes = !known.get(index) || pageSlices[index] != page.sliceHash();
            pageSlices[index] = page.sliceHash();
            published.set(index);
            known.set(index);
            if (changes) {
                if (changedCount == changed.length) {
                    changed = Arrays.copyOf(changed, changedCount * 2);
                }
                changed[changedCount++] = index;
                state.paths().pageChanged(state.tick());
            }
            sliceBytes[index] = page.slice();
            decoded[index] = null; // A repeated capture in this publication replaces its effective slice too.
            if (page.sampleTick() >= 0) {
                terrainEdits.dropUpTo(index, page.sampleTick());
                losses(index, page.coord());
            }
            if (terrainEdits.count(index) > 0 || footprints.any(index)) {
                built.add(rebuilt(index, page.coord(), decoded));
                rebuild.clear(index);
            } else {
                built.add(page.page());
            }
        }
        surfaces = surfaces.publishAll(built);
        large.publish(surfaces, built, sliceBytes, terrainEdits, footprints, decoded);
        if (!state.nodes().discovered() && published.cardinality() == pageSlices.length) {
            state.discoverNodes(sliceBytes);
        }
    }

    /** Reconcile resource blocks changed outside the core. */
    private void losses(int page, PageCoord coord) {
        MatchResourceDiscovery.losses(state, sliceBytes, terrainEdits, page, coord);
    }

    private SurfacePage rebuilt(int index, PageCoord coord, @Nullable VoxelSlice[] decoded) {
        VoxelSlice slice = Objects.requireNonNull(LargeNavigation.slice(index, sliceBytes, terrainEdits, decoded));
        return rebuilder.build(slice, footprints.of(coord), coord);
    }

    /**
     * The core sets a voxel itself, at match-local column (x, z) and absolute height y: a log it removed, a footprint
     * it cleared. Its page is rebuilt in this tick's WORLD phase; the writer makes the block follow.
     */
    public void editVoxel(int x, int y, int z, short cls) {
        MatchBounds b = state.config().bounds();
        int band = y - b.minY();
        if (x < 0 || z < 0 || x >= b.sizeX() || z >= b.sizeZ() || band < 0 || band >= VoxelSlice.MAX_HEIGHT) {
            throw new IllegalArgumentException("voxel (" + x + ", " + y + ", " + z + ") outside the match space");
        }
        int index = pageIndex(PageCoord.of(x, z));
        terrainEdits.add(index, TerrainEdits.cell(x & 15, band, z & 15), cls, state.tick());
        rebuild.set(index);
    }

    /** Whether a building's footprint blocks match-local column (x, z). */
    public boolean footprintAt(int x, int z) {
        return footprints.blocked(x, z);
    }

    /** Blocks a footprint column (BLD-01); its page is rebuilt in this tick's WORLD phase. */
    public void blockColumn(int x, int z) {
        footprints.block(x, z);
        rebuild.set(footprints.pageOf(x, z));
    }

    /** Blocks a restored footprint's column, before the pages are published: no rebuild follows. */
    public void restoreColumn(int x, int z) {
        footprints.block(x, z);
    }

    /** Releases a footprint column; its page is rebuilt in this tick's WORLD phase. */
    public void releaseColumn(int x, int z) {
        footprints.release(x, z);
        rebuild.set(footprints.pageOf(x, z));
    }

    /**
     * WORLD: rebuilds every page the core edited this tick, from its last slice, and publishes them as changed. A page
     * not captured yet gets its edits when it is first published.
     */
    public void rebuildEdited() {
        if (rebuild.isEmpty()) {
            return;
        }
        List<SurfacePage> built = new ArrayList<>();
        @Nullable VoxelSlice[] decoded = new VoxelSlice[sliceBytes.length];
        for (int i = rebuild.nextSetBit(0); i >= 0; i = rebuild.nextSetBit(i + 1)) {
            if (sliceBytes[i] == null) {
                continue;
            }
            built.add(rebuilt(i, new PageCoord(i % surfaces.pagesX(), i / surfaces.pagesX()), decoded));
            if (changedCount == changed.length) {
                changed = Arrays.copyOf(changed, changedCount * 2);
            }
            changed[changedCount++] = i;
            state.paths().pageChanged(state.tick());
        }
        rebuild.clear();
        if (!built.isEmpty()) {
            surfaces = surfaces.publishAll(built);
            large.publish(surfaces, built, sliceBytes, terrainEdits, footprints, decoded);
        }
    }

    /** The core's own terrain edits (M3 spec §1.6 row 5). */
    public TerrainEdits terrainEdits() {
        return terrainEdits;
    }

    /**
     * A restored page whose slice is missing: it stays unpublished, but its slice hash is known, so the same slice
     * published again (a restart's prewarm) does not count as a change.
     */
    void expect(PageRef ref) {
        int index = pageIndex(new PageCoord(ref.x(), ref.z()));
        pageSlices[index] = ref.slice();
        known.set(index);
    }

    private int pageIndex(PageCoord c) {
        if (c.x() >= surfaces.pagesX() || c.z() >= surfaces.pagesZ()) {
            throw new IllegalArgumentException(
                    "page " + c + " outside the match's " + surfaces.pagesX() + " x " + surfaces.pagesZ() + " pages");
        }
        return c.z() * surfaces.pagesX() + c.x();
    }

    /** How many pages changed this tick. */
    public int changedPageCount() {
        return changedCount;
    }

    public int changedPage(int k) {
        if (k >= changedCount) {
            throw new IndexOutOfBoundsException("changed page " + k + " of " + changedCount);
        }
        return changed[k];
    }

    /** The published pages, in page order, by the hash of the slice each was built from. */
    public List<PageRef> pageRefs() {
        List<PageRef> refs = new ArrayList<>(published.cardinality());
        for (int i = published.nextSetBit(0); i >= 0; i = published.nextSetBit(i + 1)) {
            refs.add(new PageRef(i % surfaces.pagesX(), i / surfaces.pagesX(), pageSlices[i]));
        }
        return refs;
    }
}
