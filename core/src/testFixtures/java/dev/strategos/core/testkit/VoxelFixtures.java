package dev.strategos.core.testkit;

import dev.strategos.core.sim.nav.BlockClass;

/**
 * Block classes as the adapter's table gives them for common blocks, and ASCII terrain for L1 (PATH-02). In a height
 * map each character is one column: a digit {@code d} is solid ground up to height {@code d} (its floor at {@code d});
 * {@code ~} is one block of water over ground at height 1; {@code =} two blocks of water over ground at 1; {@code #}
 * a solid wall 6 high; {@code !} lava over ground at 1; {@code .} is ground at 1. Rows run along X, lines along Z.
 */
public final class VoxelFixtures {
    public static final short AIR = BlockClass.AIR;
    public static final short SOLID = BlockClass.SOLID;
    public static final short SLAB = BlockClass.of(8, BlockClass.STANDABLE);
    public static final short CARPET = BlockClass.of(1, BlockClass.STANDABLE);
    public static final short PATH = BlockClass.of(15, BlockClass.STANDABLE);
    public static final short STAIRS = BlockClass.of(16, BlockClass.STANDABLE);
    public static final short FENCE = BlockClass.of(24, 0);
    public static final short WALL = BlockClass.of(24, 0);
    public static final short DOOR = BlockClass.of(16, BlockClass.DOOR);
    public static final short WATER = BlockClass.of(0, BlockClass.PASSABLE | BlockClass.WATER);
    public static final short LAVA = BlockClass.of(0, BlockClass.PASSABLE | BlockClass.LAVA | BlockClass.HAZARD);
    public static final short LEAVES = BlockClass.of(16, BlockClass.LEAVES);
    /** A sweet berry bush: no collision, a hazard to walk into, as the mod's table classifies it. */
    public static final short BUSH = BlockClass.of(0, BlockClass.PASSABLE | BlockClass.HAZARD);

    public static final short MAGMA = BlockClass.of(16, BlockClass.STANDABLE | BlockClass.HAZARD);
    public static final short SOUL_SAND = BlockClass.of(14, BlockClass.STANDABLE | BlockClass.SLOW);
    public static final short LADDER = BlockClass.of(16, BlockClass.CLIMBABLE);

    private VoxelFixtures() {}

    /** A snow layer stack of {@code layers} (1–8): vanilla's collision is one layer lower. */
    public static short snow(int layers) {
        int top = (layers - 1) * 2;
        return top == 0 ? AIR : BlockClass.of(top, BlockClass.STANDABLE);
    }

    /** Solid ground of {@code ground} blocks under a {@code sizeX x sizeZ} world {@code height} high. */
    public static VoxelWorld flat(int sizeX, int sizeZ, int height, int ground) {
        VoxelWorld w = new VoxelWorld(sizeX, height, sizeZ);
        if (ground > 0) {
            w.fill(0, 0, 0, sizeX - 1, ground - 1, sizeZ - 1, SOLID);
        }
        return w;
    }

    /** A height map, one line per Z, one character per X; see the class comment for the characters. */
    public static VoxelWorld heightmap(int height, String map) {
        String[] lines = map.strip().lines().toArray(String[]::new);
        int sizeZ = lines.length;
        int sizeX = lines[0].strip().length();
        VoxelWorld w = new VoxelWorld(sizeX, height, sizeZ);
        for (int z = 0; z < sizeZ; z++) {
            String line = lines[z].strip();
            if (line.length() != sizeX) {
                throw new IllegalArgumentException("line " + z + " has " + line.length() + " columns, not " + sizeX);
            }
            for (int x = 0; x < sizeX; x++) {
                char ch = line.charAt(x);
                switch (ch) {
                    case '~' -> w.set(x, 0, z, SOLID).set(x, 1, z, WATER);
                    case '=' -> w.set(x, 0, z, SOLID).set(x, 1, z, WATER).set(x, 2, z, WATER);
                    case '#' -> w.fill(x, 0, z, x, 5, z, SOLID);
                    case '!' -> w.set(x, 0, z, SOLID).set(x, 1, z, LAVA);
                    case '.' -> w.set(x, 0, z, SOLID);
                    default -> {
                        if (ch < '0' || ch > '9') {
                            throw new IllegalArgumentException("unknown terrain character '" + ch + "'");
                        }
                        int ground = ch - '0';
                        if (ground > 0) {
                            w.fill(x, 0, z, x, ground - 1, z, SOLID);
                        }
                    }
                }
            }
        }
        return w;
    }
}
