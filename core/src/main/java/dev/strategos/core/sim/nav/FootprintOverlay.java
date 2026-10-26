package dev.strategos.core.sim.nav;

import dev.strategos.core.shared.CoreApi;
import java.util.Arrays;

/**
 * The columns the core blocks itself (04 §9.2, M3 spec §1.6 row 6): a building's footprint from its commit to its
 * removal, so units never stand inside it. Mutable, per page of the match; {@link #of} gives the immutable
 * {@link NavOverlay} one page's rebuild reads.
 */
@CoreApi
public final class FootprintOverlay {
    private final int pagesX;
    private final long[][] masks;
    private final int[] blockedCounts;
    private int activePages;

    public FootprintOverlay(int pagesX, int pagesZ) {
        this.pagesX = pagesX;
        this.masks = new long[pagesX * pagesZ][4];
        this.blockedCounts = new int[pagesX * pagesZ];
    }

    /** The page index of match-local column (x, z). */
    public int pageOf(int x, int z) {
        return (z >> 4) * pagesX + (x >> 4);
    }

    /** Blocks match-local column (x, z); counted, so two footprints on one column need two releases. */
    public void block(int x, int z) {
        int page = pageOf(x, z);
        int column = SurfacePage.column(x & 15, z & 15);
        masks[page][column >> 6] |= 1L << (column & 63);
        if (blockedCounts[page]++ == 0) activePages++;
    }

    /** Releases match-local column (x, z). */
    public void release(int x, int z) {
        int page = pageOf(x, z);
        if (blockedCounts[page] == 0) {
            return;
        }
        if (--blockedCounts[page] == 0) {
            activePages--;
            Arrays.fill(masks[page], 0); // the last footprint on the page is gone
            return;
        }
        int column = SurfacePage.column(x & 15, z & 15);
        masks[page][column >> 6] &= ~(1L << (column & 63));
    }

    /** Whether match-local column (x, z) is blocked. */
    public boolean blocked(int x, int z) {
        int page = pageOf(x, z);
        int column = SurfacePage.column(x & 15, z & 15);
        return blockedCounts[page] > 0 && (masks[page][column >> 6] & (1L << (column & 63))) != 0;
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
        long[] mask = masks[page];
        NavOverlay.Builder b = NavOverlay.builder();
        for (int column = 0; column < 256; column++) {
            if ((mask[column >> 6] & (1L << (column & 63))) != 0) {
                b.block(coord.x() * 16 + column / 16, coord.z() * 16 + column % 16); // column = x * 16 + z
            }
        }
        return b.build();
    }
}
