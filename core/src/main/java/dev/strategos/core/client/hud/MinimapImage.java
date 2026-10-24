package dev.strategos.core.client.hud;

/**
 * The minimap's terrain image (UI-11, D-16), one pixel a column, as vanilla's maps draw it: each column's map colour
 * (its top block's {@code MapColor}, RGB) shaded by its height against the column north of it, brighter where it
 * rises ({@value #HIGH}), plain where level ({@value #NORMAL}), darker where it falls ({@value #LOW}); a column with no
 * colour is transparent. The mod builds it a chunk at a time; drawing scales it into the HUD's square.
 */
public final class MinimapImage {
    /** Vanilla's map brightnesses: lower, level, higher than the column to the north. */
    public static final int LOW = 180;

    public static final int NORMAL = 220;
    public static final int HIGH = 255;

    /** The columns the image reads: a column's map colour (0 for none) and its top block's height. */
    public interface Columns {
        int colour(int x, int z);

        int height(int x, int z);
    }

    private MinimapImage() {}

    /** Column ({@code x}, {@code z})'s pixel, ARGB. */
    public static int pixel(Columns columns, int x, int z) {
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

    /** The 16 × 16 pixels of chunk ({@code cx}, {@code cz}) of a {@code sizeX} × {@code sizeZ} space, row by row. */
    public static int[] tile(Columns columns, int cx, int cz, int sizeX, int sizeZ) {
        int[] out = new int[256];
        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int x = cx * 16 + dx;
                int z = cz * 16 + dz;
                out[dz * 16 + dx] = x < sizeX && z < sizeZ ? pixel(columns, x, z) : 0;
            }
        }
        return out;
    }

    /** The whole image, row by row, {@code sizeX} wide. */
    public static int[] image(Columns columns, int sizeX, int sizeZ) {
        int[] out = new int[sizeX * sizeZ];
        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                out[z * sizeX + x] = pixel(columns, x, z);
            }
        }
        return out;
    }
}
