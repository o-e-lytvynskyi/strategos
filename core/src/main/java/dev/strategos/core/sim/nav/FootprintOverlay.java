package dev.strategos.core.sim.nav;

import dev.strategos.core.shared.CoreApi;

/**
 * The columns the core blocks itself (04 §9.2, M3 spec §1.6 row 6): a building's footprint from its commit to its
 * removal, so units never stand inside it. Mutable, per page of the match, a count per column (a gate inside a
 * footprint, or two footprints, need one release each); {@link #of} gives the immutable
 * {@link NavOverlay} one page's rebuild reads.
 */
@CoreApi
public final class FootprintOverlay {
    private final int pagesX;
    private final short[][] counts; // per page, per column: how many blockers stand on it
    private final int[] blockedCounts; // per page: how many columns are blocked
    private int activePages;

    public FootprintOverlay(int pagesX, int pagesZ) {
        this.pagesX = pagesX;
        this.counts = new short[pagesX * pagesZ][]; // a page's counts are made when its first column is blocked
        this.blockedCounts = new int[pagesX * pagesZ];
    }

    /** An independent copy of the columns blocked now. */
    public FootprintOverlay copy() {
        FootprintOverlay copy = new FootprintOverlay(pagesX, counts.length / pagesX);
        for (int page = 0; page < counts.length; page++) {
            if (blockedCounts[page] > 0) {
                copy.counts[page] = counts[page].clone();
                copy.blockedCounts[page] = blockedCounts[page];
            }
        }
        copy.activePages = activePages;
        return copy;
    }

    /** The page index of match-local column (x, z). */
    public int pageOf(int x, int z) {
        return (z >> 4) * pagesX + (x >> 4);
    }

    /** Blocks match-local column (x, z); counted, so two blockers on one column need two releases. */
    public void block(int x, int z) {
        int page = pageOf(x, z);
        int column = SurfacePage.column(x & 15, z & 15);
        if (counts[page] == null) counts[page] = new short[256];
        if (counts[page][column]++ == 0 && blockedCounts[page]++ == 0) activePages++;
    }

    /** Releases match-local column (x, z) once. */
    public void release(int x, int z) {
        int page = pageOf(x, z);
        int column = SurfacePage.column(x & 15, z & 15);
        if (counts[page] == null || counts[page][column] == 0) {
            return;
        }
        if (--counts[page][column] == 0 && --blockedCounts[page] == 0) {
            activePages--;
        }
    }

    /** Whether match-local column (x, z) is blocked. */
    public boolean blocked(int x, int z) {
        short[] page = counts[pageOf(x, z)];
        return page != null && page[SurfacePage.column(x & 15, z & 15)] > 0;
    }

    /** Whether any footprint is present, including restored columns. */
    public boolean any() {
        return activePages > 0;
    }

    /** Whether page {@code page} has a blocked column. */
    public boolean any(int page) {
        return blockedCounts[page] > 0;
    }

    /** Page {@code coord}'s blocked columns as the page builder reads them. */
    public NavOverlay of(PageCoord coord) {
        int page = coord.z() * pagesX + coord.x();
        if (blockedCounts[page] == 0) {
            return NavOverlay.EMPTY;
        }
        short[] count = counts[page];
        NavOverlay.Builder b = NavOverlay.builder();
        for (int column = 0; column < 256; column++) {
            if (count[column] > 0) {
                b.block(coord.x() * 16 + column / 16, coord.z() * 16 + column % 16); // column = x * 16 + z
            }
        }
        return b.build();
    }
}
