package dev.strategos.core.client.hud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.shared.TerritoryGrid;
import org.junit.jupiter.api.Test;

/** TERR-03.3: the minimap's border layer, the pixels of a 2 × 2-tile layout (32 × 16 blocks, two chunks). */
class MinimapBordersTest {
    private static final HudTheme THEME = HudTheme.DEFAULT;

    private static final MinimapImage.Columns FLAT = new MinimapImage.Columns() {
        @Override
        public int colour(int x, int z) {
            return 0x808080;
        }

        @Override
        public int height(int x, int z) {
            return 64;
        }
    };

    /** Tiles (0,0) faction 0, (1,0) faction 1; row 1 neutral. */
    private static TerritoryGrid grid() {
        TerritoryGrid g = new TerritoryGrid(4, 2);
        g.set(0, (byte) (TerritoryGrid.CONNECTED | 0));
        g.set(1, (byte) (TerritoryGrid.CONNECTED | 1));
        return g;
    }

    private static int[] chunk(int cx) {
        return MinimapImage.tile(FLAT, MinimapImage.Fog.NONE, grid(), THEME, cx, 0, 32, 16);
    }

    @Test
    void ownedTilesAreOutlinedInTheirOwnersColourWhereTheNeighbourDiffers() {
        int[] c0 = chunk(0);
        int blue = THEME.faction(0);
        // tile (0,0): its west and north edges (map border and neutral), south (neutral row) and east (faction 1)
        assertThat(c0[0]).isEqualTo(blue); // (0,0)
        assertThat(c0[7]).isEqualTo(blue); // (7,0): the north row, east column
        assertThat(c0[7 * 16 + 7]).isEqualTo(blue); // (7,7): the south row
        assertThat(c0[3 * 16 + 7]).isEqualTo(blue); // (7,3): the east column, faction 1's neighbour
        assertThat(c0[3 * 16 + 3]).isNotEqualTo(blue); // inside the tile: terrain
        // tile (1,0) is faction 1's: its west column faces faction 0, drawn in red; a neutral tile draws none
        assertThat(c0[3 * 16 + 8]).isEqualTo(THEME.faction(1));
        assertThat(c0[3 * 16 + 11]).isNotEqualTo(THEME.faction(1));
    }

    @Test
    void neutralLandAndNoGridKeepTheTerrain() {
        int[] c0 = chunk(0);
        assertThat(c0[10 * 16 + 3]).isNotEqualTo(THEME.faction(0)); // row 1 of tiles is neutral
        int[] plain = MinimapImage.tile(FLAT, MinimapImage.Fog.NONE, null, THEME, 0, 0, 32, 16);
        assertThat(plain).isEqualTo(MinimapImage.tile(FLAT, 0, 0, 32, 16));
    }

    @Test
    void anEdgeBetweenTwoOwnedTilesOfOneOwnerIsNotDrawn() {
        TerritoryGrid g = new TerritoryGrid(4, 2);
        g.set(0, (byte) 0);
        g.set(1, (byte) 0);
        assertThat(MinimapImage.borderPixel(g, THEME, 7, 3)).isZero();
        assertThat(MinimapImage.borderPixel(g, THEME, 8, 3)).isZero();
        assertThat(MinimapImage.borderPixel(g, THEME, 15, 3)).isEqualTo(THEME.faction(0));
    }
}
