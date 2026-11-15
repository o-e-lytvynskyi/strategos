package dev.strategos.core.client.hud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.client.fog.ShroudExtract;
import dev.strategos.core.shared.Hasher;
import org.junit.jupiter.api.Test;

/**
 * UI-16.1: a fixed grid's minimap pixels under fog (unexplored black, explored half brightness, visible clear) hash as
 * stored.
 */
class MinimapFogTest {
    /** 8×8 blocks → 2×2 vision cells of {@link ShroudExtract#CELL_BLOCKS}. */
    private static final int SIZE = 8;

    /**
     * Vision: cell (0,0) visible+explored, (1,0) explored only, (0,1) and (1,1) unexplored. Bits packed as cell index
     * {@code z * cellsX + x}.
     */
    private static final long VISIBLE = 1L; // cell 0

    private static final long EXPLORED = 1L | (1L << 1); // cells 0 and 1

    /** Pinned hash of {@link #pixelsUnderFogEqualTheHash}'s image; a fog or shade change updates it with a reason. */
    static final long STORED = 0xcea158f42ccbbd3dL;

    @Test
    void pixelsUnderFogEqualTheHash() {
        MinimapImage.Columns columns = flatWhite();
        MinimapImage.Fog fog = MinimapImage.fromVision(new long[] {VISIBLE}, new long[] {EXPLORED}, 2, 2);
        int[] image = MinimapImage.image(columns, fog, SIZE, SIZE);
        assertThat(image).hasSize(SIZE * SIZE);

        // Visible cell (0,0): blocks [0,4)×[0,4) — terrain at NORMAL shade of white.
        int clear = 0xFFDCDCDC;
        // Explored cell (1,0): blocks [4,8)×[0,4) — half brightness.
        int dim = MinimapImage.halfBrightness(clear);
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                int px = image[z * SIZE + x];
                int cx = x / ShroudExtract.CELL_BLOCKS;
                int cz = z / ShroudExtract.CELL_BLOCKS;
                if (cx == 0 && cz == 0) {
                    assertThat(px).as("visible (%d,%d)", x, z).isEqualTo(clear);
                } else if (cx == 1 && cz == 0) {
                    assertThat(px).as("explored (%d,%d)", x, z).isEqualTo(dim);
                } else {
                    assertThat(px).as("unexplored (%d,%d)", x, z).isEqualTo(MinimapImage.UNEXPLORED);
                }
            }
        }

        long h = Hasher.start(0);
        for (int px : image) {
            h = Hasher.mix(h, px);
        }
        assertThat(Long.toHexString(Hasher.finish(h))).isEqualTo(Long.toHexString(STORED));
    }

    private static MinimapImage.Columns flatWhite() {
        return new MinimapImage.Columns() {
            @Override
            public int colour(int x, int z) {
                return 0xFFFFFF;
            }

            @Override
            public int height(int x, int z) {
                return 5;
            }
        };
    }
}
