package dev.strategos.core.client.hud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.shared.Hasher;
import dev.strategos.core.testkit.ProvingGround;
import org.junit.jupiter.api.Test;

/**
 * UI-11.1: the proving ground's minimap, its columns' vanilla map colours (the {@code MapColor} of each palette block,
 * RGB) shaded by height as vanilla's maps shade, hashes as stored; a tile is the image's own square.
 */
class MinimapImageTest {
    /** The proving ground's palette, by index, as vanilla's MapColor gives each block's colour. */
    static final int[] MAP_COLOURS = {
        0, // air
        0x707070, // stone: STONE
        0x7FB238, // grass block: GRASS
        0x976D4D, // dirt: DIRT
        0x4040FF, // water: WATER
        0xF7E9A3, // sand: SAND
        0x8F7748, // oak planks: WOOD
        0x8F7748, // fences and the gate: WOOD
        0x8F7748,
        0x8F7748,
        0x707070, // stone bricks: STONE
        0x8F7748, // an oak log's top: WOOD
        0x007C00, // leaves: PLANT
        0x8F7748,
        0x707070, // cobblestone: STONE
        0x007C00 // a berry bush: PLANT
    };

    /** The hash the proving ground v2's minimap image is pinned to; a terrain change updates it with the map. */
    static final long STORED = 0x973a70bdd225deb3L;

    static MinimapImage.Columns columns(ProvingGround pg) {
        return new MinimapImage.Columns() {
            @Override
            public int colour(int x, int z) {
                int y = pg.top(x, z);
                return y < 0 ? 0 : MAP_COLOURS[pg.block(x, y, z)];
            }

            @Override
            public int height(int x, int z) {
                return pg.top(x, z);
            }
        };
    }

    @Test
    void theProvingGroundsPixelsHashAsStored() {
        ProvingGround pg = ProvingGround.generate();
        int[] image = MinimapImage.image(columns(pg), pg.size(), pg.size());
        long h = Hasher.start(0);
        for (int px : image) {
            h = Hasher.mix(h, px);
        }
        assertThat(Long.toHexString(Hasher.finish(h))).isEqualTo(Long.toHexString(STORED));
        int[] tile = MinimapImage.tile(columns(pg), 3, 7, pg.size(), pg.size());
        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                assertThat(tile[dz * 16 + dx]).isEqualTo(image[(7 * 16 + dz) * pg.size() + 3 * 16 + dx]);
            }
        }
    }

    @Test
    void heightShadesAsVanillasMaps() {
        int[][] heights = {{5, 5}, {6, 4}}; // by z, then x
        MinimapImage.Columns c = new MinimapImage.Columns() {
            @Override
            public int colour(int x, int z) {
                return 0xFFFFFF;
            }

            @Override
            public int height(int x, int z) {
                return heights[z][x];
            }
        };
        assertThat(MinimapImage.pixel(c, 0, 0)).as("the north row is level").isEqualTo(0xFFDCDCDC);
        assertThat(MinimapImage.pixel(c, 0, 1)).as("rising: brighter").isEqualTo(0xFFFFFFFF);
        assertThat(MinimapImage.pixel(c, 1, 1)).as("falling: darker").isEqualTo(0xFFB4B4B4);
    }
}
