package dev.strategos.core.testkit;

import dev.strategos.core.content.MapWaypoint;
import dev.strategos.core.content.StartSlot;
import dev.strategos.core.content.TerrainHash;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.Rng;
import dev.strategos.core.sim.nav.Material;
import dev.strategos.core.sim.nav.MaterialKind;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * The proving ground (spec §6.4, MAP-01): a deterministic map that exercises every movement case. Over rolling hills
 * it has a mesa with two ramps and a staircase of 1-block steps, a cliff band with one ramp through it, a river two
 * blocks deep with two fords one block deep, a plank bridge with railings and a stone arch, a forest, a 3-block-wide
 * pass between cliffs, and a walled yard with a gate and wall walkways; start slots near the west and east edges.
 * Version 2 (M3 T9, ECO-05) adds for the first loop a grove of twelve oaks near each start slot and eight berry bushes
 * behind it, the east's the west's turned half round the map's centre, so the two starts are alike. Version 3 (M4
 * T22, AI-02) adds a wood of thirty oaks north of each start, its near edge inside the territory, so an economy outlasts
 * its grove without walking to the forest, which lies nearer the east start.
 *
 * <p>It is the source of truth: {@code MapgenDriver} writes the map package from it, the manifest stores its terrain
 * hash, and L1 builds the same terrain from it ({@link #voxels()}). Heights are combined with min and max of functions
 * that change by at most one block between neighbouring columns, so slopes stay walkable and cliffs are only where they
 * are meant. Water is still: source blocks inside banks at least as high. Grass is never under another block, so random
 * ticks cannot turn it to dirt; leaves are persistent.
 */
public final class ProvingGround {
    public static final int SIZE = 384;
    public static final int HEIGHT = 64;
    public static final int TILE = 32;

    /** Block states as Minecraft writes them; a voxel holds an index. Stone is the base block. */
    public static final List<String> PALETTE = List.of(
            "minecraft:air",
            "minecraft:stone",
            "minecraft:grass_block",
            "minecraft:dirt",
            "minecraft:water",
            "minecraft:sand",
            "minecraft:oak_planks",
            "minecraft:oak_fence[east=true,west=true]",
            "minecraft:oak_fence[east=true]",
            "minecraft:oak_fence[west=true]",
            "minecraft:stone_bricks",
            "minecraft:oak_log",
            "minecraft:oak_leaves[persistent=true]",
            "minecraft:oak_fence_gate[facing=east]",
            "minecraft:cobblestone",
            "minecraft:sweet_berry_bush[age=3]");

    public static final byte AIR = 0;
    public static final byte STONE = 1;
    public static final byte GRASS = 2;
    public static final byte DIRT = 3;
    public static final byte WATER = 4;
    public static final byte SAND = 5;
    public static final byte PLANKS = 6;
    public static final byte FENCE = 7;
    public static final byte FENCE_WEST_END = 8;
    public static final byte FENCE_EAST_END = 9;
    public static final byte STONE_BRICKS = 10;
    public static final byte LOG = 11;
    public static final byte LEAVES = 12;
    public static final byte GATE = 13;
    public static final byte COBBLESTONE = 14;
    public static final byte BUSH = 15;

    /** The map's version: 3 since the woods of M4 T22; 2 since the groves and bushes of M3 T9. */
    public static final int VERSION = 3;

    /** What a log, a leaf and a bush are to the economy, as the mod's {@code strategos:resource_yield} entries say. */
    public static final Material LOG_MATERIAL = new Material(MaterialKind.LOG, "wood", "tree", 20_000);

    public static final Material LEAF_MATERIAL = new Material(MaterialKind.LEAVES, "", "", 0);
    public static final Material BUSH_MATERIAL = new Material(MaterialKind.FORAGE, "food", "fruit", 60_000, 100);

    /** The river's middle column; it runs north to south, 7 wide. */
    public static final int RIVER_X = 192;

    /** The top water block: the river's surface sits at Y 11, its banks no lower. */
    public static final int WATER_TOP = 11;

    private static final long SEED = 0x5072_6F76_696E_67L;

    private final int size;
    private final byte[] blocks;
    private final int[] top;
    private final Map<String, MapWaypoint> waypoints = new TreeMap<>();
    private final List<StartSlot> startSlots;

    private ProvingGround(int size) {
        if (size < SIZE) {
            throw new IllegalArgumentException("the proving ground needs at least " + SIZE + " blocks, not " + size);
        }
        this.size = size;
        this.blocks = new byte[size * size * HEIGHT];
        this.top = new int[size * size];
        int[] ground = heights();
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                int h = ground[x * size + z];
                for (int y = 0; y < h; y++) {
                    set(x, y, z, STONE);
                }
                set(x, h, z, GRASS);
            }
        }
        river();
        bridges();
        yard();
        forest();
        groves();
        if (!Boolean.getBoolean("pg.v2")) {
            woods();
        } // TEMP A/B
        noGrassUnderBlocks();
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                int y = HEIGHT - 1;
                while (y > 0 && block(x, y, z) == AIR) {
                    y--;
                }
                top[x * size + z] = y;
            }
        }
        int middle = size / 2;
        startSlots = List.of(
                new StartSlot(24, floor(24, middle), middle, 270f),
                new StartSlot(size - 24, floor(size - 24, middle), middle, 90f));
        waypoint("west", 24, middle);
        waypoint("east", size - 24, middle);
        waypoint("far_side", size - 44, middle);
        waypoints.put("ford", new MapWaypoint(RIVER_X, WATER_TOP, 143));
        waypoints.put("bridge", new MapWaypoint(RIVER_X, 13, 96));
        waypoint("pass", 268, 128);
        waypoint("yard", 316, 260);
    }

    /** The 384² proving ground. */
    public static ProvingGround generate() {
        return generate(SIZE);
    }

    /** The same features on a larger square, for the 512² benchmarks: the rest is hills. */
    public static ProvingGround generate(int size) {
        return new ProvingGround(size);
    }

    public int size() {
        return size;
    }

    /** The palette index at map-local {@code x}, {@code y}, {@code z}. */
    public byte block(int x, int y, int z) {
        return blocks[(x * size + z) * HEIGHT + y];
    }

    /** The highest block of a column that is not air. */
    public int top(int x, int z) {
        return top[x * size + z];
    }

    /**
     * The lowest block a tile stores for a column: the lowest that is not stone, the base block, or the top when the
     * column is stone throughout; the importer fills stone below it. When that block is air (a gate's opening under its
     * lintel), the stone under it is stored too, since the importer fills up to the lowest block a tile holds and air is
     * never stored.
     */
    public int lowestStored(int x, int z) {
        int highest = top(x, z);
        for (int y = 0; y < highest; y++) {
            if (block(x, y, z) != STONE) {
                return block(x, y, z) == AIR ? y - 1 : y;
            }
        }
        return highest;
    }

    public List<StartSlot> startSlots() {
        return startSlots;
    }

    public Map<String, MapWaypoint> waypoints() {
        return Map.copyOf(waypoints);
    }

    public String terrainHash() {
        return TerrainHash.of(size, size, HEIGHT, (x, y, z) -> block(x, y, z) & 0xFF);
    }

    /** The terrain as block classes, as the mod's table classifies these blocks: for L1 movement tests. */
    public VoxelWorld voxels() {
        VoxelWorld world = new VoxelWorld(size, HEIGHT, size);
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                for (int y = 0; y <= top(x, z); y++) {
                    byte b = block(x, y, z);
                    if (b != AIR) {
                        world.set(x, y, z, classOf(b));
                    }
                    Material m = materialOf(b);
                    if (m != null) {
                        world.material(x, y, z, m);
                    }
                }
            }
        }
        return world;
    }

    private static short classOf(byte b) {
        return switch (b) {
            case WATER -> VoxelFixtures.WATER;
            case FENCE, FENCE_WEST_END, FENCE_EAST_END -> VoxelFixtures.FENCE;
            case LEAVES -> VoxelFixtures.LEAVES;
            case GATE -> VoxelFixtures.DOOR;
            case BUSH -> VoxelFixtures.BUSH;
            case AIR -> VoxelFixtures.AIR;
            default -> VoxelFixtures.SOLID;
        };
    }

    private static @Nullable Material materialOf(byte b) {
        return switch (b) {
            case LOG -> LOG_MATERIAL;
            case LEAVES -> LEAF_MATERIAL;
            case BUSH -> BUSH_MATERIAL;
            default -> null;
        };
    }

    /** The ground's top block per column: hills, then the shapes cut and raised into them. */
    private int[] heights() {
        int[] h = new int[size * size];
        int middle = size / 2;
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                double hills = 3.0 * DetMath.sin(2 * Math.PI * x / 97 + 0.7)
                        + 2.0 * DetMath.sin(2 * Math.PI * z / 71 + 1.3)
                        + 1.5 * DetMath.sin(2 * Math.PI * (x + z) / 53 + 2.1);
                int g = 15 + (int) Math.round(hills);
                g = level(g, 15, distance(x, z, 8, middle - 16, 40, middle + 16));
                g = level(g, 15, distance(x, z, size - 40, middle - 16, size - 8, middle + 16));
                g = level(g, 15, distance(x, z, 296, 240, 335, 279));
                g = cliffBand(g, x, z);
                g = mesa(g, x, z);
                g = ridge(g, x, z);
                int fromBank = Math.abs(x - RIVER_X) - 3;
                g = Math.max(WATER_TOP, Math.min(g, WATER_TOP + Math.floorDiv(fromBank, 2)));
                h[x * size + z] = g;
            }
        }
        return h;
    }

    /** Pulls {@code g} to {@code level} inside a rectangle, at most one block per block of distance outside it. */
    private static int level(int g, int level, int distance) {
        return Math.max(level - distance, Math.min(g, level + distance));
    }

    /** The Chebyshev distance from a column to a rectangle, 0 inside. */
    private static int distance(int x, int z, int x0, int z0, int x1, int z1) {
        int dx = Math.max(0, Math.max(x0 - x, x - x1));
        int dz = Math.max(0, Math.max(z0 - z, z - z1));
        return Math.max(dx, dz);
    }

    /**
     * A plateau four blocks up from X 100 to 150: its west edge is a cliff, except for a ramp one block per two at the
     * crossing (Z 186–198); its east edge tapers off, walkable everywhere.
     */
    private int cliffBand(int g, int x, int z) {
        if (z < 16 || z > size - 17 || x < 92 || x > 158) {
            return g;
        }
        boolean ramp = z >= 186 && z <= 198;
        int add;
        if (x < 100) {
            add = ramp ? (x - 91 + 1) / 2 : 0;
        } else if (x <= 150) {
            add = 4;
        } else {
            add = 4 - (x - 150 + 1) / 2;
        }
        return g + add;
    }

    /**
     * A mesa at X 40–70, Z 40–70, its top at 22, its sides cliffs above a ring kept at 16 or lower; a ramp down the
     * north side and one down the south side (one block per two), and a staircase of 1-block steps down the east side.
     * Ramps and stairs go on down to Y 10 and give way to the ground wherever it is higher, so they meet it smoothly.
     */
    private static int mesa(int g, int x, int z) {
        int d = distance(x, z, 40, 40, 70, 70);
        if (d == 0) {
            return 22;
        }
        int ground = Math.min(g, 16 + Math.max(0, d - 6));
        if (x >= 52 && x <= 58 && z >= 16 && z <= 39) {
            return Math.max(ground, 22 - (39 - z + 2) / 2);
        }
        if (x >= 52 && x <= 58 && z >= 71 && z <= 94) {
            return Math.max(ground, 22 - (z - 71 + 2) / 2);
        }
        if (z >= 54 && z <= 56 && x >= 71 && x <= 82) {
            return Math.max(ground, 22 - (x - 70));
        }
        return ground;
    }

    /** A ridge at X 256–280, Z 96–160, with its top at 30, cut by a pass 3 blocks wide along X at Z 127–129. */
    private static int ridge(int g, int x, int z) {
        if (x < 256 || x > 280 || z < 96 || z > 160 || (z >= 127 && z <= 129)) {
            return g;
        }
        return 30;
    }

    /**
     * The river: X 189–195 from Z 4 to the far edge less 5, sand at Y 9 under water at 10 and 11, and at the fords (Z
     * 140–146 and 236–242) sand at 10 under water at 11. The banks around are no lower than the surface.
     */
    private void river() {
        for (int x = RIVER_X - 3; x <= RIVER_X + 3; x++) {
            for (int z = 4; z <= size - 5; z++) {
                boolean ford = (z >= 140 && z <= 146) || (z >= 236 && z <= 242);
                int bed = ford ? 10 : 9;
                for (int y = 0; y < HEIGHT; y++) {
                    set(x, y, z, y < bed ? STONE : y == bed ? SAND : y <= WATER_TOP ? WATER : AIR);
                }
            }
        }
    }

    /**
     * A plank bridge (Z 94–98) with fence railings on its edges over the water, and a stone arch (Z 286–290) with
     * parapets and its feet in the river; both decks at Y 12, a block above the banks' floor.
     */
    private void bridges() {
        for (int x = RIVER_X - 5; x <= RIVER_X + 5; x++) {
            for (int z = 94; z <= 98; z++) {
                set(x, 12, z, PLANKS);
                clearAbove(x, 12, z);
            }
            for (int z = 286; z <= 290; z++) {
                set(x, 12, z, STONE_BRICKS);
                clearAbove(x, 12, z);
            }
        }
        for (int x = RIVER_X - 3; x <= RIVER_X + 3; x++) {
            byte fence = x == RIVER_X - 3 ? FENCE_WEST_END : x == RIVER_X + 3 ? FENCE_EAST_END : FENCE;
            set(x, 13, 94, fence);
            set(x, 13, 98, fence);
            set(x, 13, 286, STONE_BRICKS);
            set(x, 13, 290, STONE_BRICKS);
        }
        for (int z = 286; z <= 290; z++) {
            for (int y = 10; y <= 11; y++) {
                set(RIVER_X - 3, y, z, STONE_BRICKS);
                set(RIVER_X + 3, y, z, STONE_BRICKS);
            }
        }
    }

    /**
     * The walled yard at X 296–335, Z 240–279 on ground leveled to 15: stone brick walls two thick and five high, whose
     * top is a walkway; a 3-wide gate of fence gates in the west wall, two blocks high under a lintel; a cobblestone
     * staircase of 1-block steps up to the walkway inside the north wall.
     */
    private void yard() {
        for (int x = 296; x <= 335; x++) {
            for (int z = 240; z <= 279; z++) {
                boolean wall = x <= 297 || x >= 334 || z <= 241 || z >= 278;
                if (!wall) {
                    continue;
                }
                set(x, 15, z, STONE);
                for (int y = 16; y <= 20; y++) {
                    boolean gate = x <= 297 && z >= 258 && z <= 260 && y <= 17;
                    set(x, y, z, gate ? (x == 296 && y == 16 ? GATE : AIR) : STONE_BRICKS);
                }
            }
        }
        for (int step = 0; step <= 4; step++) {
            for (int z = 242; z <= 243; z++) {
                for (int y = 16; y <= 16 + step; y++) {
                    set(300 + step, y, z, COBBLESTONE);
                }
            }
        }
    }

    /**
     * Oaks in a 7-block grid over X 208–248, Z 296–360, each shifted up to 2 blocks by a hash of its cell: a trunk of 4
     * or 5 logs on dirt, a 5 x 5 canopy two layers deep without its corners, then 3 x 3, then a cross.
     */
    private void forest() {
        for (int cx = 0; cx < 6; cx++) {
            for (int cz = 0; cz < 10; cz++) {
                long cell = cx * 31L + cz;
                int x = 210 + cx * 7 + Rng.below(SEED, 1, 0, cell, 1, 3);
                int z = 298 + cz * 7 + Rng.below(SEED, 1, 0, cell, 2, 3);
                int trunk = 4 + Rng.below(SEED, 1, 0, cell, 3, 2);
                int ground = groundTop(x, z);
                set(x, ground, z, DIRT);
                int crown = ground + trunk;
                for (int y = ground + 1; y <= crown; y++) {
                    set(x, y, z, LOG);
                }
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                        if (!corner) {
                            leaf(x + dx, crown - 1, z + dz);
                            leaf(x + dx, crown, z + dz);
                        }
                        if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                            leaf(x + dx, crown + 1, z + dz);
                        }
                        if (Math.abs(dx) + Math.abs(dz) <= 1) {
                            leaf(x + dx, crown + 2, z + dz);
                        }
                    }
                }
            }
        }
    }

    /**
     * Version 2: near the west start (24, 192) twelve oaks in a 4 x 3 grid 7 apart over X 56–77, Z 222–236, south of
     * the crossings' lanes, and behind it, to the west, eight berry bushes at X 3 and 6, Z 184, 187, 197 and 200, past
     * the 14 blocks the soaks spawn within; the east start gets the same turned half round the centre (x, z to size - 1
     * - x, size - 1 - z).
     */
    private void groves() {
        for (int cx = 0; cx < 4; cx++) {
            for (int cz = 0; cz < 3; cz++) {
                int trunk = 4 + (cx + cz) % 2;
                oak(56 + cx * 7, 222 + cz * 7, trunk);
                oak(size - 1 - (56 + cx * 7), size - 1 - (222 + cz * 7), trunk);
            }
        }
        for (int x : new int[] {3, 6}) {
            for (int z : new int[] {184, 187, 197, 200}) {
                bush(x, z);
                bush(size - 1 - x, size - 1 - z);
            }
        }
    }

    /**
     * Version 3: north of each start thirty oaks in a 5 x 6 grid 7 apart over Z 120–155, the west's over X 8–36 and the
     * east's over X 347–375, turned round the X axis only: turned half round the centre it would stand in the east's
     * yard. The nearest is about 38 blocks from its start, clear of the workers' yard 26 blocks north of the west one
     * and of the crowds' way along the starts' row (a wood nearer it sent a crossing's crowd round and jammed a ford).
     */
    private void woods() {
        for (int cx = 0; cx < 5; cx++) {
            for (int cz = 0; cz < 6; cz++) {
                int trunk = 4 + (cx + cz) % 2;
                oak(8 + cx * 7, 120 + cz * 7, trunk);
                oak(size - 1 - (8 + cx * 7), 120 + cz * 7, trunk);
            }
        }
    }

    private void bush(int x, int z) {
        int ground = groundTop(x, z);
        set(x, ground, z, DIRT);
        set(x, ground + 1, z, BUSH);
    }

    /** An oak as the forest's: a trunk on dirt, a 5 x 5 canopy two layers deep without its corners, 3 x 3, a cross. */
    private void oak(int x, int z, int trunk) {
        int ground = groundTop(x, z);
        set(x, ground, z, DIRT);
        int crown = ground + trunk;
        for (int y = ground + 1; y <= crown; y++) {
            set(x, y, z, LOG);
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                if (!corner) {
                    leaf(x + dx, crown - 1, z + dz);
                    leaf(x + dx, crown, z + dz);
                }
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                    leaf(x + dx, crown + 1, z + dz);
                }
                if (Math.abs(dx) + Math.abs(dz) <= 1) {
                    leaf(x + dx, crown + 2, z + dz);
                }
            }
        }
    }

    private void leaf(int x, int y, int z) {
        if (block(x, y, z) == AIR) {
            set(x, y, z, LEAVES);
        }
    }

    /** Grass with a block on it would turn to dirt on a random tick, so it is dirt from the start. */
    private void noGrassUnderBlocks() {
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                for (int y = 0; y < HEIGHT - 1; y++) {
                    if (block(x, y, z) == GRASS && block(x, y + 1, z) != AIR) {
                        set(x, y, z, DIRT);
                    }
                }
            }
        }
    }

    private int groundTop(int x, int z) {
        int y = HEIGHT - 1;
        while (y > 0 && block(x, y, z) == AIR) {
            y--;
        }
        return y;
    }

    private void clearAbove(int x, int y, int z) {
        for (int above = y + 1; above < HEIGHT; above++) {
            set(x, above, z, AIR);
        }
    }

    /** The block a unit stands in at a column: above its top. */
    private int floor(int x, int z) {
        return top(x, z) + 1;
    }

    private void waypoint(String name, int x, int z) {
        waypoints.put(name, new MapWaypoint(x, floor(x, z), z));
    }

    private void set(int x, int y, int z, byte b) {
        blocks[(x * size + z) * HEIGHT + y] = b;
    }
}
