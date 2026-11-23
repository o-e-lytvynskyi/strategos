package dev.strategos.core.client.territory;

import dev.strategos.core.shared.HotPath;
import dev.strategos.core.shared.TerritoryGrid;
import java.util.Arrays;
import java.util.BitSet;
import java.util.function.IntBinaryOperator;
import java.util.function.IntPredicate;

/**
 * The territory borders as quads (M9 spec §2.11, TERR-03): where a tile's neighbour has another owner, a strip of
 * {@value #WIDTH} blocks inside the owned tile runs along that edge, {@value #LIFT} over the floor. Each owned side
 * draws its own strip in its owner's colour relative to the viewer (own, ally, enemy); a neutral side draws nothing.
 * An edge of a tile not connected to its owner's root is dashed: every other {@value #PIECE}-block piece. A tile lies
 * inside one section column (tiles are 8 blocks, sections 16), so a column's geometry reads only its own tiles and
 * their neighbours. Extracted into a reusable {@link Buffer}, one section column at a time.
 */
@HotPath
public final class BorderGeometry {
    /** Distance of the quads over the floor block's top. */
    public static final float LIFT = 0.05F;

    /** A strip's width in blocks, inside the owned tile. */
    public static final float WIDTH = 0.4F;

    /** The blocks of one piece of a strip; a dashed edge keeps every other piece. */
    public static final int PIECE = 2;

    /** Blocks per section column side. */
    public static final int SECTION = 16;

    /** ARGB of the viewer's own borders, an ally's and anyone else's. */
    public static final int OWN = 0xD03B82F6;

    public static final int ALLY = 0xD022C55E;
    public static final int ENEMY = 0xD0EF4444;

    private static final int TILES_PER_SECTION = SECTION / TerritoryGrid.TILE;
    private static final int PIECES_PER_EDGE = TerritoryGrid.TILE / PIECE;

    private BorderGeometry() {}

    /**
     * Quads of one section column: per quad four corners of x, y, z (x and z in blocks from the column's smallest
     * corner, y as the floor gave it plus {@link #LIFT}), wound counter-clockwise seen from above, and one ARGB colour.
     * Arrays grow and are reused, never shrunk.
     */
    public static final class Buffer {
        private float[] coords = new float[12 * 64];
        private int[] colours = new int[64];
        private int count;

        public int count() {
            return count;
        }

        /** Corner {@code c} (0..3) of quad {@code q}: X. */
        public float x(int q, int c) {
            return coords[q * 12 + c * 3];
        }

        public float y(int q, int c) {
            return coords[q * 12 + c * 3 + 1];
        }

        public float z(int q, int c) {
            return coords[q * 12 + c * 3 + 2];
        }

        public int colour(int q) {
            return colours[q];
        }

        public void clear() {
            count = 0;
        }

        /** A copy of the quads' corners, 12 floats a quad. */
        public float[] coordsCopy() {
            return Arrays.copyOf(coords, count * 12);
        }

        /** A copy of the quads' colours. */
        public int[] coloursCopy() {
            return Arrays.copyOf(colours, count);
        }

        /** A quad seen from above: (x0, z0), (x0, z1), (x1, z1), (x1, z0) is counter-clockwise on the screen. */
        void add(float x0, float z0, float x1, float z1, float y, int colour) {
            if (count == colours.length) {
                colours = Arrays.copyOf(colours, count * 2);
                coords = Arrays.copyOf(coords, count * 24);
            }
            int i = count * 12;
            coords[i] = x0;
            coords[i + 1] = y;
            coords[i + 2] = z0;
            coords[i + 3] = x0;
            coords[i + 4] = y;
            coords[i + 5] = z1;
            coords[i + 6] = x1;
            coords[i + 7] = y;
            coords[i + 8] = z1;
            coords[i + 9] = x1;
            coords[i + 10] = y;
            coords[i + 11] = z0;
            colours[count++] = colour;
        }
    }

    /** The section columns across a grid {@code tiles} tiles wide. */
    public static int sectionsFor(int tiles) {
        return (tiles + TILES_PER_SECTION - 1) / TILES_PER_SECTION;
    }

    /**
     * Extracts section column ({@code sectionX}, {@code sectionZ}) (match-local blocks / 16) into {@code out}, which is
     * cleared first. {@code floorY} gives the floor's block height at a match-local block column; {@code allied} says
     * whether a faction is the viewer's ally.
     */
    public static void extract(
            TerritoryGrid grid,
            int sectionX,
            int sectionZ,
            int viewer,
            IntPredicate allied,
            IntBinaryOperator floorY,
            Buffer out) {
        out.clear();
        int tx0 = sectionX * TILES_PER_SECTION;
        int tz0 = sectionZ * TILES_PER_SECTION;
        for (int tz = tz0; tz < tz0 + TILES_PER_SECTION; tz++) {
            for (int tx = tx0; tx < tx0 + TILES_PER_SECTION; tx++) {
                int owner = grid.owner(tx, tz);
                if (owner < 0) {
                    continue;
                }
                int colour = owner == viewer ? OWN : allied.test(owner) ? ALLY : ENEMY;
                int stride = grid.connected(tx, tz) ? 1 : 2; // dashed: every other piece
                int bx = tx * TerritoryGrid.TILE;
                int bz = tz * TerritoryGrid.TILE;
                int lx = bx - sectionX * SECTION;
                int lz = bz - sectionZ * SECTION;
                int far = TerritoryGrid.TILE - 1;
                for (int p = 0; p < PIECES_PER_EDGE; p += stride) {
                    int a = p * PIECE;
                    if (grid.owner(tx - 1, tz) != owner) {
                        float y = floorY.applyAsInt(bx, bz + a) + LIFT;
                        out.add(lx, lz + a, lx + WIDTH, lz + a + PIECE, y, colour);
                    }
                    if (grid.owner(tx + 1, tz) != owner) {
                        float y = floorY.applyAsInt(bx + far, bz + a) + LIFT;
                        out.add(
                                lx + TerritoryGrid.TILE - WIDTH,
                                lz + a,
                                lx + TerritoryGrid.TILE,
                                lz + a + PIECE,
                                y,
                                colour);
                    }
                    if (grid.owner(tx, tz - 1) != owner) {
                        float y = floorY.applyAsInt(bx + a, bz) + LIFT;
                        out.add(lx + a, lz, lx + a + PIECE, lz + WIDTH, y, colour);
                    }
                    if (grid.owner(tx, tz + 1) != owner) {
                        float y = floorY.applyAsInt(bx + a, bz + far) + LIFT;
                        out.add(
                                lx + a,
                                lz + TerritoryGrid.TILE - WIDTH,
                                lx + a + PIECE,
                                lz + TerritoryGrid.TILE,
                                y,
                                colour);
                    }
                }
            }
        }
    }

    /**
     * The section columns whose geometry may change when the tiles in {@code changed} do: a changed tile's own column
     * and the columns of its four neighbours, whose strips read its owner. Bit {@code sz * sectionsFor(tilesX) + sx}.
     */
    public static BitSet sectionsTouched(BitSet changed, int tilesX, int tilesZ) {
        int sectionsX = sectionsFor(tilesX);
        int sectionsZ = sectionsFor(tilesZ);
        BitSet out = new BitSet(sectionsX * sectionsZ);
        for (int t = changed.nextSetBit(0); t >= 0 && t < tilesX * tilesZ; t = changed.nextSetBit(t + 1)) {
            int tx = t % tilesX;
            int tz = t / tilesX;
            mark(out, tx, tz, tilesX, tilesZ, sectionsX);
            mark(out, tx - 1, tz, tilesX, tilesZ, sectionsX);
            mark(out, tx + 1, tz, tilesX, tilesZ, sectionsX);
            mark(out, tx, tz - 1, tilesX, tilesZ, sectionsX);
            mark(out, tx, tz + 1, tilesX, tilesZ, sectionsX);
        }
        return out;
    }

    private static void mark(BitSet out, int tx, int tz, int tilesX, int tilesZ, int sectionsX) {
        if (tx >= 0 && tz >= 0 && tx < tilesX && tz < tilesZ) {
            out.set((tz / TILES_PER_SECTION) * sectionsX + tx / TILES_PER_SECTION);
        }
    }
}
