package dev.strategos.core.testkit;

import dev.strategos.core.sim.nav.BlockClass;
import dev.strategos.core.sim.nav.Material;
import dev.strategos.core.sim.nav.MaterialKind;

/**
 * Trees for L1 (ECO-03): the shapes of vanilla's species drawn into a {@link VoxelWorld} with their materials, a log
 * worth {@link #PER_LOG} wood: straight 1 × 1 trunks (oak, birch, spruce, cherry, mangrove), 2 × 2 trunks (dark oak,
 * jungle, giant spruce), and leaning or branching ones (acacia, fancy oak). Shapes only, drawn from how the species
 * look in game, not from vanilla's feature code; each canopy is whole, within six leaf steps of a log, as a grown tree's
 * is. The ground is a one-block floor.
 */
public final class TreeFixtures {
    public static final long PER_LOG = 20_000;
    public static final Material LOG = new Material(MaterialKind.LOG, "wood", "tree", PER_LOG);
    public static final Material LEAF = new Material(MaterialKind.LEAVES, "", "", 0);
    public static final short LEAVES = BlockClass.of(16, BlockClass.LEAVES);

    /** The species drawn here. */
    public enum Species {
        OAK,
        BIRCH,
        SPRUCE,
        CHERRY,
        MANGROVE,
        DARK_OAK,
        JUNGLE,
        GIANT_SPRUCE,
        ACACIA,
        FANCY_OAK
    }

    private TreeFixtures() {}

    /** A 32 x 32 floor, height 32, with one tree of {@code species} at (12, 1, 12). */
    public static VoxelWorld one(Species species) {
        VoxelWorld w = VoxelFixtures.flat(32, 32, 32, 1);
        draw(w, species, 12, 1, 12);
        return w;
    }

    /** Draws {@code species} with its trunk's corner at (x, y, z). */
    public static void draw(VoxelWorld w, Species species, int x, int y, int z) {
        switch (species) {
            case OAK -> straight(w, x, y, z, 5, 2);
            case BIRCH -> straight(w, x, y, z, 7, 2);
            case SPRUCE -> cone(w, x, y, z, 1, 9);
            case CHERRY -> {
                straight(w, x, y, z, 4, 3);
            }
            case MANGROVE -> {
                log(w, x - 1, y, z); // a prop root
                log(w, x + 1, y, z);
                straight(w, x, y, z, 6, 2);
            }
            case DARK_OAK -> wide(w, x, y, z, 6, 3);
            case JUNGLE -> wide(w, x, y, z, 12, 3);
            case GIANT_SPRUCE -> cone(w, x, y, z, 2, 14);
            case ACACIA -> {
                for (int k = 0; k < 4; k++) {
                    log(w, x, y + k, z);
                }
                for (int k = 1; k <= 3; k++) { // the lean: diagonal steps
                    log(w, x + k, y + 3 + k, z + k);
                }
                blob(w, x + 3, y + 7, z + 3, 3, 1);
            }
            case FANCY_OAK -> {
                for (int k = 0; k < 8; k++) {
                    log(w, x, y + k, z);
                }
                log(w, x + 1, y + 5, z); // branches, one diagonal
                log(w, x + 2, y + 6, z + 1);
                log(w, x - 1, y + 6, z - 1);
                blob(w, x, y + 8, z, 3, 2);
                blob(w, x + 2, y + 7, z + 1, 2, 1);
                blob(w, x - 1, y + 7, z - 1, 2, 1);
            }
        }
    }

    /** A 1 x 1 trunk {@code height} high under a round canopy of {@code radius}. */
    static void straight(VoxelWorld w, int x, int y, int z, int height, int radius) {
        for (int k = 0; k < height; k++) {
            log(w, x, y + k, z);
        }
        blob(w, x, y + height - 1, z, radius, 2);
        leaf(w, x, y + height, z);
        leaf(w, x, y + height + 1, z);
    }

    /** A 2 x 2 trunk under a broad canopy. */
    static void wide(VoxelWorld w, int x, int y, int z, int height, int radius) {
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                for (int k = 0; k < height; k++) {
                    log(w, x + dx, y + k, z + dz);
                }
            }
        }
        blob(w, x, y + height - 1, z, radius, 2);
        blob(w, x + 1, y + height - 1, z + 1, radius, 2);
    }

    /** A spruce: a trunk of width {@code width} with rings of leaves narrowing upward. */
    static void cone(VoxelWorld w, int x, int y, int z, int width, int height) {
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < width; dz++) {
                for (int k = 0; k < height; k++) {
                    log(w, x + dx, y + k, z + dz);
                }
            }
        }
        for (int k = 2; k < height + 1; k++) {
            int r = Math.max(1, (height - k) / 3 + width - 1);
            if (k % 2 == 0) {
                ring(w, x, y + k, z, width, r);
            }
        }
        leaf(w, x, y + height, z);
    }

    /** A layer of leaves round the trunk, as far as {@code r} steps from it (at most five, so a log holds every one). */
    private static void ring(VoxelWorld w, int x, int y, int z, int width, int r) {
        int reach = Math.min(5, r);
        for (int dx = -reach; dx < width + reach; dx++) {
            for (int dz = -reach; dz < width + reach; dz++) {
                int ox = dx < 0 ? -dx : Math.max(0, dx - width + 1);
                int oz = dz < 0 ? -dz : Math.max(0, dz - width + 1);
                if (ox + oz <= reach) {
                    leaf(w, x + dx, y, z + dz);
                }
            }
        }
    }

    /** A canopy round (x, y, z): {@code layers} layers of radius {@code r}, corners cut, then one narrower. */
    static void blob(VoxelWorld w, int x, int y, int z, int r, int layers) {
        for (int l = 0; l < layers; l++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) == r && Math.abs(dz) == r) {
                        continue;
                    }
                    leaf(w, x + dx, y - l, z + dz);
                }
            }
        }
        for (int dx = -r + 1; dx <= r - 1; dx++) {
            for (int dz = -r + 1; dz <= r - 1; dz++) {
                leaf(w, x + dx, y + 1, z + dz);
            }
        }
    }

    static void log(VoxelWorld w, int x, int y, int z) {
        w.set(x, y, z, BlockClass.SOLID);
        w.material(x, y, z, LOG);
    }

    static void leaf(VoxelWorld w, int x, int y, int z) {
        if (x < 0 || z < 0 || x >= w.sizeX() || z >= w.sizeZ() || y < 0 || y >= w.height()) {
            return;
        }
        if (w.get(x, y, z) == BlockClass.AIR) {
            w.set(x, y, z, LEAVES);
            w.material(x, y, z, LEAF);
        }
    }
}
