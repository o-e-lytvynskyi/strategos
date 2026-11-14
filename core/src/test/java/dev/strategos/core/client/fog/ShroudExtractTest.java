package dev.strategos.core.client.fog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** FOW-07: shroud alphas, soft corners, and page rebuild only when cells change. */
class ShroudExtractTest {
    @Test
    void cellAlphaIsClearExploredOrBlack() {
        int cells = 4;
        long[] visible = new long[ShroudExtract.words(cells, cells)];
        long[] explored = new long[visible.length];
        ShroudExtract.setBit(explored, 0); // (0,0) explored
        ShroudExtract.setBit(visible, 1); // (1,0) visible
        ShroudExtract.setBit(explored, 1);
        assertThat(ShroudExtract.cellAlpha(visible, explored, cells, cells, 0, 0))
                .isEqualTo(ShroudExtract.EXPLORED);
        assertThat(ShroudExtract.cellAlpha(visible, explored, cells, cells, 1, 0))
                .isEqualTo(ShroudExtract.VISIBLE);
        assertThat(ShroudExtract.cellAlpha(visible, explored, cells, cells, 2, 0))
                .isEqualTo(ShroudExtract.UNEXPLORED);
        assertThat(ShroudExtract.cellAlpha(visible, explored, cells, cells, -1, 0))
                .isEqualTo(ShroudExtract.UNEXPLORED);
    }

    @Test
    void softCornersAverageTheCellsThatMeet() {
        int cells = 2;
        long[] visible = new long[ShroudExtract.words(cells, cells)];
        long[] explored = new long[visible.length];
        // (0,0) visible, (1,0) explored, rest unexplored
        ShroudExtract.setBit(visible, 0);
        ShroudExtract.setBit(explored, 0);
        ShroudExtract.setBit(explored, 1);
        // Interior corner (1,1) meets all four cells: 0 + 0.5 + 1 + 1 = 2.5 / 4
        assertThat(ShroudExtract.cornerAlpha(visible, explored, cells, cells, 1, 1))
                .isCloseTo(2.5F / 4F, within(1e-5F));
        // Corner (0,0) meets only (0,0): visible → 0
        assertThat(ShroudExtract.cornerAlpha(visible, explored, cells, cells, 0, 0))
                .isEqualTo(ShroudExtract.VISIBLE);
    }

    @Test
    void aFullyVisiblePageEmitsNoQuadsAndSkipsRebuildWhenUnchanged() {
        int cells = ShroudExtract.PAGE;
        long[] visible = new long[ShroudExtract.words(cells, cells)];
        long[] explored = new long[visible.length];
        for (int i = 0; i < cells * cells; i++) {
            ShroudExtract.setBit(visible, i);
            ShroudExtract.setBit(explored, i);
        }
        ShroudExtract.PageMesh mesh = new ShroudExtract.PageMesh();
        assertThat(ShroudExtract.extractPage(visible, explored, cells, cells, 0, 0, mesh))
                .isTrue();
        assertThat(mesh.quads()).isZero();
        long fp = mesh.fingerprint();
        assertThat(ShroudExtract.extractPage(visible, explored, cells, cells, 0, 0, mesh))
                .isFalse();
        assertThat(mesh.fingerprint()).isEqualTo(fp);
        assertThat(mesh.quads()).isZero();
    }

    @Test
    void unexploredAndExploredCellsBecomeQuadsWithSoftEdges() {
        int cells = 4;
        long[] visible = new long[ShroudExtract.words(cells, cells)];
        long[] explored = new long[visible.length];
        ShroudExtract.setBit(explored, 0); // one explored cell at (0,0); none visible
        ShroudExtract.PageMesh mesh = new ShroudExtract.PageMesh();
        assertThat(ShroudExtract.extractPage(visible, explored, cells, cells, 0, 0, mesh))
                .isTrue();
        assertThat(mesh.quads()).isEqualTo(cells * cells);
        // Cell (0,0): SW corner is only that cell → explored 0.5; SE averages (0,0) and (1,0)
        float se = ShroudExtract.cornerAlpha(visible, explored, cells, cells, 1, 0);
        assertThat(mesh.alpha(0, 0)).isEqualTo(ShroudExtract.EXPLORED);
        assertThat(mesh.alpha(0, 1)).isEqualTo(se);
        assertThat(mesh.x(0, 0)).isEqualTo(0);
        assertThat(mesh.z(0, 0)).isEqualTo(0);
        assertThat(mesh.x(0, 1)).isEqualTo(ShroudExtract.CELL_BLOCKS);
        // Changing a cell rebuilds; same fingerprint skips
        ShroudExtract.setBit(visible, 0);
        assertThat(ShroudExtract.extractPage(visible, explored, cells, cells, 0, 0, mesh))
                .isTrue();
        assertThat(ShroudExtract.extractPage(visible, explored, cells, cells, 0, 0, mesh))
                .isFalse();
    }

    @Test
    void pagesCoverA384Match() {
        int blocks = 384;
        int cells = blocks / ShroudExtract.CELL_BLOCKS; // 96
        assertThat(ShroudExtract.pages(cells)).isEqualTo(6);
        assertThat(cells * ShroudExtract.CELL_BLOCKS).isEqualTo(blocks);
    }
}
