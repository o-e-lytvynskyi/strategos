package dev.strategos.core.content;

import dev.strategos.core.shared.Rng;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * M3's placeholder buildings (BLD-01, ART-01), generated in code so construction never waits for art: a civic centre,
 * a house and a barracks of non-flammable blocks, each a floor, walls with a doorway on its north side (z = 0) and a
 * roof. The mod's {@code .nbt} structures are written from these and read back equal (BLD-01.1); owner-built
 * blueprints may replace them later. Build order, damage ranks and markers follow {@link #shape}.
 */
public final class PlaceholderBlueprints {
    public static final String CIVIC_CENTRE = "strategos:civic_centre";
    public static final String HOUSE = "strategos:house";
    public static final String BARRACKS = "strategos:barracks";

    private PlaceholderBlueprints() {}

    /** Every placeholder. */
    public static List<Blueprint> all() {
        return List.of(civicCentre(), house(), barracks());
    }

    /** Every placeholder, by id, in id order. */
    public static Map<String, Blueprint> byId() {
        Map<String, Blueprint> out = new TreeMap<>();
        for (Blueprint b : all()) {
            out.put(b.id(), b);
        }
        return out;
    }

    /** 11 x 7 x 11: stone-brick walls with a three-wide doorway, a flat roof and a smaller cap. */
    public static Blueprint civicCentre() {
        Grid g = new Grid(
                11,
                7,
                11,
                List.of(
                        "minecraft:stone_bricks",
                        "minecraft:cobblestone",
                        "minecraft:polished_andesite",
                        "minecraft:chiseled_stone_bricks"));
        g.fill(0, 0, 0, 10, 0, 10, 1);
        for (int y = 1; y <= 4; y++) {
            g.ring(0, y, 0, 10, 10, y == 4 ? 3 : 0);
        }
        g.clear(4, 1, 0, 6, 3, 0); // the doorway
        g.fill(0, 5, 0, 10, 5, 10, 0);
        g.fill(2, 6, 2, 8, 6, 8, 2);
        return g.blueprint(CIVIC_CENTRE, 5, 1, 0);
    }

    /** 5 x 5 x 5: packed-mud walls on a cobblestone floor, crimson-plank corner posts and roof (crimson wood never burns). */
    public static Blueprint house() {
        Grid g = new Grid(
                5,
                5,
                5,
                List.of(
                        "minecraft:cobblestone",
                        "minecraft:packed_mud",
                        "minecraft:crimson_planks",
                        "minecraft:mud_bricks"));
        g.fill(0, 0, 0, 4, 0, 4, 0);
        for (int y = 1; y <= 3; y++) {
            g.ring(0, y, 0, 4, 4, 1);
            g.set(0, y, 0, 2);
            g.set(4, y, 0, 2);
            g.set(0, y, 4, 2);
            g.set(4, y, 4, 2);
        }
        g.clear(2, 1, 0, 2, 2, 0); // the doorway
        g.fill(0, 4, 0, 4, 4, 4, 2);
        return g.blueprint(HOUSE, 2, 1, 0);
    }

    /** 9 x 6 x 7: mud-brick walls on cobblestone, a crimson-plank roof and a ridge. */
    public static Blueprint barracks() {
        Grid g = new Grid(
                9,
                6,
                7,
                List.of(
                        "minecraft:cobblestone",
                        "minecraft:mud_bricks",
                        "minecraft:crimson_planks",
                        "minecraft:stone_bricks"));
        g.fill(0, 0, 0, 8, 0, 6, 0);
        for (int y = 1; y <= 3; y++) {
            g.ring(0, y, 0, 8, 6, y == 3 ? 3 : 1);
        }
        g.clear(4, 1, 0, 4, 2, 0); // the doorway
        g.fill(0, 4, 0, 8, 4, 6, 2);
        g.fill(0, 5, 3, 8, 5, 3, 2);
        return g.blueprint(BARRACKS, 4, 1, 0);
    }

    /**
     * A blueprint of {@code cells} (by x, then y, then z, each a palette index or -1 for none) in build order: Y up,
     * then X, then Z. Ranks: -1 for the two lowest layers of the outer ring, which damage never removes (04 §11.8:
     * no passages); every other block a hash of the id and its index, raised above the block under it, so damage
     * takes a block only after what it holds up. The door marker and the spawn marker stand in the doorway.
     */
    static Blueprint shape(
            String id, int sx, int sy, int sz, List<String> palette, int[] cells, int dx, int dy, int dz) {
        List<int[]> blocks = new ArrayList<>();
        int[] rankAt = new int[sx * sy * sz];
        java.util.Arrays.fill(rankAt, -2); // no block
        List<Integer> ranks = new ArrayList<>();
        for (int y = 0; y < sy; y++) {
            for (int x = 0; x < sx; x++) {
                for (int z = 0; z < sz; z++) {
                    int c = cells[(x * sy + y) * sz + z];
                    if (c < 0) {
                        continue;
                    }
                    int index = blocks.size();
                    blocks.add(new int[] {x, y, z, c});
                    boolean ring = x == 0 || z == 0 || x == sx - 1 || z == sz - 1;
                    int rank;
                    if (ring && y <= 1) {
                        rank = -1;
                    } else {
                        rank = (int) (Rng.mix64(id.hashCode() * 31L + index) & 0x3FF);
                        int below = y > 0 ? rankAt[(x * sy + y - 1) * sz + z] : -2;
                        if (below >= rank) {
                            rank = below + 1;
                        }
                    }
                    rankAt[(x * sy + y) * sz + z] = rank;
                    ranks.add(rank);
                }
            }
        }
        int[] flat = new int[blocks.size() * 4];
        for (int i = 0; i < blocks.size(); i++) {
            System.arraycopy(blocks.get(i), 0, flat, i * 4, 4);
        }
        return new Blueprint(
                id,
                sx,
                sy,
                sz,
                palette,
                flat,
                ranks.stream().mapToInt(Integer::intValue).toArray(),
                List.of(new Blueprint.Marker("door", dx, dy, dz), new Blueprint.Marker("spawn", dx, dy, dz)));
    }

    /** The problems of a blueprint's own rules (04 §11.7–11.8): its door, its ranks; flammable blocks are the mod's. */
    public static List<String> problems(Blueprint b) {
        List<String> out = new ArrayList<>();
        if (b.marker("door") == null) {
            out.add(b.id() + " has no door marker");
        }
        for (Blueprint.Marker m : b.markers()) {
            if (!Blueprint.markerKind(m.kind())) {
                out.add(b.id() + " has a marker of unknown kind '" + m.kind() + "'");
            }
        }
        int[] rankAt = new int[b.sizeX() * b.sizeY() * b.sizeZ()];
        java.util.Arrays.fill(rankAt, -2);
        for (int k = 0; k < b.count(); k++) {
            if (b.x(k) < 0
                    || b.y(k) < 0
                    || b.z(k) < 0
                    || b.x(k) >= b.sizeX()
                    || b.y(k) >= b.sizeY()
                    || b.z(k) >= b.sizeZ()) {
                out.add(b.id() + " block " + k + " outside its size");
                return out;
            }
            rankAt[(b.x(k) * b.sizeY() + b.y(k)) * b.sizeZ() + b.z(k)] = b.rank(k);
        }
        for (int k = 0; k < b.count(); k++) {
            int x = b.x(k);
            int y = b.y(k);
            int z = b.z(k);
            boolean ring = x == 0 || z == 0 || x == b.sizeX() - 1 || z == b.sizeZ() - 1;
            if (ring && y <= 1 && b.rank(k) != -1) {
                out.add(b.id() + " block " + k + " at (" + x + ", " + y + ", " + z
                        + ") is in the two lowest layers of the outer ring and ranked " + b.rank(k));
            }
            if (b.rank(k) >= 0 && y > 0) {
                int below = rankAt[(x * b.sizeY() + y - 1) * b.sizeZ() + z];
                if (below >= b.rank(k)) {
                    out.add(b.id() + " block " + k + " at (" + x + ", " + y + ", " + z + ") ranks " + b.rank(k)
                            + ", not above the block holding it up (" + below + ")");
                }
            }
        }
        return out;
    }

    /** A box of cells to draw a blueprint into. */
    static final class Grid {
        final int sx;
        final int sy;
        final int sz;
        final List<String> palette;
        final int[] cells;

        Grid(int sx, int sy, int sz, List<String> palette) {
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.palette = palette;
            this.cells = new int[sx * sy * sz];
            java.util.Arrays.fill(cells, -1);
        }

        void set(int x, int y, int z, int state) {
            cells[(x * sy + y) * sz + z] = state;
        }

        void fill(int x0, int y0, int z0, int x1, int y1, int z1, int state) {
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        set(x, y, z, state);
                    }
                }
            }
        }

        void clear(int x0, int y0, int z0, int x1, int y1, int z1) {
            fill(x0, y0, z0, x1, y1, z1, -1);
        }

        /** The ring of the rectangle (x0, z0)–(x1, z1) at height y. */
        void ring(int x0, int y, int z0, int x1, int z1, int state) {
            for (int x = x0; x <= x1; x++) {
                set(x, y, z0, state);
                set(x, y, z1, state);
            }
            for (int z = z0; z <= z1; z++) {
                set(x0, y, z, state);
                set(x1, y, z, state);
            }
        }

        Blueprint blueprint(String id, int dx, int dy, int dz) {
            return shape(id, sx, sy, sz, palette, cells, dx, dy, dz);
        }
    }
}
