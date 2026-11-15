package dev.strategos.core.client.hud;

import dev.strategos.core.client.fog.ShroudExtract;

/**
 * The minimap's terrain image (UI-11, D-16), one pixel a column, as vanilla's maps draw it: each column's map colour
 * (its top block's {@code MapColor}, RGB) shaded by its height against the column north of it, brighter where it
 * rises ({@value #HIGH}), plain where level ({@value #NORMAL}), darker where it falls ({@value #LOW}); a column with no
 * colour is transparent. UI-16 composes fog over that: unexplored black, explored at half brightness, visible clear.
 * The mod builds it a chunk at a time; drawing scales it into the HUD's square.
 */
public final class MinimapImage {
    /** Vanilla's map brightnesses: lower, level, higher than the column to the north. */
    public static final int LOW = 180;

    public static final int NORMAL = 220;
    public static final int HIGH = 255;

    /** Opaque black for an unexplored column (UI-16). */
    public static final int UNEXPLORED = 0xFF000000;

    /** The columns the image reads: a column's map colour (0 for none) and its top block's height. */
    public interface Columns {
        int colour(int x, int z);

        int height(int x, int z);
    }

    /**
     * Fog over match-local block columns (UI-16): unexplored hides terrain; explored dims it; visible leaves it clear.
     * {@link #NONE} treats every column as visible.
     */
    public interface Fog {
        /** No fog: every column is visible. */
        Fog NONE = new Fog() {
            @Override
            public boolean visible(int x, int z) {
                return true;
            }

            @Override
            public boolean explored(int x, int z) {
                return true;
            }
        };

        boolean visible(int x, int z);

        boolean explored(int x, int z);
    }

    private MinimapImage() {}

    /** Column ({@code x}, {@code z})'s pixel, ARGB, with no fog. */
    public static int pixel(Columns columns, int x, int z) {
        return pixel(columns, Fog.NONE, x, z);
    }

    /** Column ({@code x}, {@code z})'s pixel, ARGB, under {@code fog}. */
    public static int pixel(Columns columns, Fog fog, int x, int z) {
        if (!fog.explored(x, z)) {
            return UNEXPLORED;
        }
        int terrain = terrainPixel(columns, x, z);
        if (fog.visible(x, z)) {
            return terrain;
        }
        return halfBrightness(terrain);
    }

    /** The 16 × 16 pixels of chunk ({@code cx}, {@code cz}) of a {@code sizeX} × {@code sizeZ} space, row by row. */
    public static int[] tile(Columns columns, int cx, int cz, int sizeX, int sizeZ) {
        return tile(columns, Fog.NONE, cx, cz, sizeX, sizeZ);
    }

    /** The 16 × 16 pixels of chunk ({@code cx}, {@code cz}) under {@code fog}. */
    public static int[] tile(Columns columns, Fog fog, int cx, int cz, int sizeX, int sizeZ) {
        int[] out = new int[256];
        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int x = cx * 16 + dx;
                int z = cz * 16 + dz;
                out[dz * 16 + dx] = x < sizeX && z < sizeZ ? pixel(columns, fog, x, z) : 0;
            }
        }
        return out;
    }

    /** The whole image, row by row, {@code sizeX} wide. */
    public static int[] image(Columns columns, int sizeX, int sizeZ) {
        return image(columns, Fog.NONE, sizeX, sizeZ);
    }

    /** The whole image under {@code fog}, row by row, {@code sizeX} wide. */
    public static int[] image(Columns columns, Fog fog, int sizeX, int sizeZ) {
        int[] out = new int[sizeX * sizeZ];
        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                out[z * sizeX + x] = pixel(columns, fog, x, z);
            }
        }
        return out;
    }

    /**
     * Fog over a client's vision bitsets: match-local block ({@code x}, {@code z}) maps to a vision cell of
     * {@link ShroudExtract#CELL_BLOCKS} blocks.
     */
    public static Fog fromVision(long[] visible, long[] explored, int cellsX, int cellsZ) {
        return new Fog() {
            @Override
            public boolean visible(int x, int z) {
                return bit(visible, cellsX, cellsZ, x, z);
            }

            @Override
            public boolean explored(int x, int z) {
                return bit(explored, cellsX, cellsZ, x, z);
            }
        };
    }

    private static boolean bit(long[] bits, int cellsX, int cellsZ, int x, int z) {
        int cx = x / ShroudExtract.CELL_BLOCKS;
        int cz = z / ShroudExtract.CELL_BLOCKS;
        if (cx < 0 || cx >= cellsX || cz < 0 || cz >= cellsZ || bits.length == 0) {
            return false;
        }
        int i = cz * cellsX + cx;
        int word = i >> 6;
        if (word >= bits.length) {
            return false;
        }
        return (bits[word] & (1L << (i & 63))) != 0;
    }

    private static int terrainPixel(Columns columns, int x, int z) {
        int rgb = columns.colour(x, z);
        if (rgb == 0) {
            return 0;
        }
        int north = z > 0 ? columns.height(x, z - 1) : columns.height(x, z);
        int h = columns.height(x, z);
        int b = h > north ? HIGH : h < north ? LOW : NORMAL;
        int r = ((rgb >> 16) & 0xFF) * b / 255;
        int g = ((rgb >> 8) & 0xFF) * b / 255;
        int bl = (rgb & 0xFF) * b / 255;
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /** Explored-and-hidden: half the RGB channels, alpha unchanged; transparent stays transparent. */
    static int halfBrightness(int argb) {
        if (argb == 0) {
            return 0;
        }
        int a = argb >>> 24;
        int r = ((argb >> 16) & 0xFF) / 2;
        int g = ((argb >> 8) & 0xFF) / 2;
        int b = (argb & 0xFF) / 2;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
