package dev.strategos.core.testkit;

import dev.strategos.core.content.MapWaypoint;
import dev.strategos.core.content.StartSlot;
import dev.strategos.core.content.TerrainHash;
import dev.strategos.core.content.TerrainJob;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.sim.map.MapChecker;
import dev.strategos.core.sim.nav.Material;
import dev.strategos.core.sim.nav.MobilityClass;
import dev.strategos.core.sim.nav.NavOverlay;
import dev.strategos.core.sim.nav.SurfaceGrid;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * The first skirmish map (MAP-02, spec §2.10, D-17): 384², 64 high, seeded, point-symmetric about its centre (x, z to
 * 383 - x, 383 - z), so the two starts are alike. West to east: the west start on a level pad, its two groves, its
 * berry bushes and room for six fields; a forest line north to south with three gaps, its oaks on the edge facing the
 * start and leaves behind them; the river, which comes down the
 * middle, splits round an island and joins again, each channel with one ford, so the island is the one crossing; on
 * the island a hill whose top plateau holds the large iron deposit; then the east half, the west's turned half round.
 * Each start's quarry, iron mine, animals and units come from the scenario {@code first_skirmish}.
 *
 * <p>It is the source of truth: {@code MapgenDriver} writes the package from it, and L1 checks it ({@link
 * #checkerInput}). Heights are min and max of functions that change by at most one block between neighbouring columns,
 * so slopes stay walkable; water is still, inside banks at least as high; grass is never under another block; leaves
 * are persistent.
 */
public final class FirstSkirmish {
    public static final int SIZE = 384;
    public static final int HEIGHT = 64;
    public static final int TILE = 32;
    public static final int VERSION = 1;
    public static final String ID = "strategos:first_skirmish";

    public static final List<String> PALETTE = List.of(
            "minecraft:air",
            "minecraft:stone",
            "minecraft:grass_block",
            "minecraft:dirt",
            "minecraft:water",
            "minecraft:sand",
            "minecraft:oak_log",
            "minecraft:oak_leaves[persistent=true]",
            "minecraft:sweet_berry_bush[age=3]");

    public static final byte AIR = 0;
    public static final byte STONE = 1;
    public static final byte GRASS = 2;
    public static final byte DIRT = 3;
    public static final byte WATER = 4;
    public static final byte SAND = 5;
    public static final byte LOG = 6;
    public static final byte LEAVES = 7;
    public static final byte BUSH = 8;

    /** What a log and a bush yield, as the mod's {@code strategos:resource_yield} entries say. */
    public static final int LOG_WOOD = 20;

    public static final int BUSH_FOOD = 60;

    public static final int WATER_TOP = 11;
    public static final int BASE = 15;
    public static final int HILL_TOP = 20;

    /** The west start; the east one is its mirror. */
    public static final int START_X = 44;

    public static final int START_Z = 140;

    /** The two ford's rows on the west channel; the east channel's are their mirror. */
    public static final int FORD_Z0 = 196;

    public static final int FORD_Z1 = 203;

    /** Water, as rectangles {x0, z0, x1, z1}: the north reach, the junctions, the two channels, the south reach. */
    private static final int[][] WATER_RECTS = {
        {188, 0, 195, 135},
        {160, 136, 223, 143},
        {160, 144, 167, 239},
        {216, 144, 223, 239},
        {160, 240, 223, 247},
        {188, 248, 195, 383}
    };

    private static final int[] ISLAND = {168, 144, 215, 239};
    private static final int[] PLATEAU = {186, 186, 197, 197};
    private static final int[] PAD = {12, 104, 84, 184};
    private static final int[] ROOM = {52, 160, 84, 184};
    private static final int FOREST_X0 = 108;
    private static final int FOREST_X1 = 112;
    private static final int[][] FOREST_GAPS = {{62, 67}, {176, 183}, {300, 305}};

    private final byte[] blocks = new byte[SIZE * SIZE * HEIGHT];
    private final int[] ground = new int[SIZE * SIZE];
    private final int[] top = new int[SIZE * SIZE];
    private final Map<String, MapWaypoint> waypoints = new TreeMap<>();
    private final List<StartSlot> startSlots;

    private FirstSkirmish() {
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                int h = height(x, z);
                ground[x * SIZE + z] = h;
                for (int y = 0; y < h; y++) {
                    set(x, y, z, STONE);
                }
                set(x, h, z, GRASS);
            }
        }
        river();
        forestLines();
        groves();
        noGrassUnderBlocks();
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                int y = HEIGHT - 1;
                while (y > 0 && block(x, y, z) == AIR) {
                    y--;
                }
                top[x * SIZE + z] = y;
            }
        }
        startSlots = List.of(
                new StartSlot(START_X, floor(START_X, START_Z), START_Z, 270f),
                new StartSlot(mirror(START_X), floor(mirror(START_X), mirror(START_Z)), mirror(START_Z), 90f));
        pair("start", START_X, START_Z);
        pair("dropsite_wood", 56, 126);
        pair("dropsite_stone", 36, 126);
        pair("dropsite_iron", 52, 122);
        pair("dropsite_food", 54, 156);
        waypoints.put("ford_west", new MapWaypoint(163, WATER_TOP, (FORD_Z0 + FORD_Z1) / 2));
        waypoints.put("ford_east", new MapWaypoint(mirror(163), WATER_TOP, mirror((FORD_Z0 + FORD_Z1) / 2)));
        waypoints.put("hill", new MapWaypoint(191, floor(191, 191), 191));
    }

    public static FirstSkirmish generate() {
        return new FirstSkirmish();
    }

    public static int mirror(int c) {
        return SIZE - 1 - c;
    }

    public byte block(int x, int y, int z) {
        return blocks[(x * SIZE + z) * HEIGHT + y];
    }

    /** The highest block of a column that is not air. */
    public int top(int x, int z) {
        return top[x * SIZE + z];
    }

    /** The ground's top block, under any tree or bush. */
    public int groundTop(int x, int z) {
        return ground[x * SIZE + z];
    }

    /** The lowest block a tile stores for a column: the lowest that is not stone (as {@code ProvingGround}'s). */
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
        return TerrainHash.of(SIZE, SIZE, HEIGHT, (x, y, z) -> block(x, y, z) & 0xFF);
    }

    /** The jobs the manifest documents and the checker verifies. */
    public List<TerrainJob> terrainJobs() {
        List<TerrainJob> jobs = new ArrayList<>();
        jobs.add(TerrainJob.hill(191, 191, 172, (FORD_Z0 + FORD_Z1) / 2, 6));
        jobs.add(TerrainJob.crossings(TerrainJob.FOREST_LINE, FOREST_X0, 0, FOREST_X1, SIZE - 1, 3, 4));
        jobs.add(TerrainJob.crossings(TerrainJob.FOREST_LINE, mirror(FOREST_X1), 0, mirror(FOREST_X0), SIZE - 1, 3, 4));
        jobs.add(river(WATER_RECTS[0], 0));
        jobs.add(river(WATER_RECTS[1], 0));
        jobs.add(river(WATER_RECTS[2], 1));
        jobs.add(river(WATER_RECTS[3], 1));
        jobs.add(river(WATER_RECTS[4], 0));
        jobs.add(river(WATER_RECTS[5], 0));
        jobs.add(TerrainJob.resource("iron", 192, 192, 12, 5_000));
        for (int k = 0; k < 2; k++) {
            int x = k == 0 ? START_X : mirror(START_X);
            int z = k == 0 ? START_Z : mirror(START_Z);
            jobs.add(TerrainJob.resource("wood", x, z, MapChecker.RESOURCE_RADIUS, 1_000));
            jobs.add(TerrainJob.resource("food", x, z, MapChecker.RESOURCE_RADIUS, 600));
            jobs.add(TerrainJob.resource("stone", x, z, MapChecker.RESOURCE_RADIUS, 1_000));
            jobs.add(TerrainJob.resource("iron", x, z, MapChecker.RESOURCE_RADIUS, 1_000));
        }
        jobs.add(TerrainJob.room(ROOM[0], ROOM[1], ROOM[2], ROOM[3], 6, 9));
        jobs.add(TerrainJob.room(mirror(ROOM[2]), mirror(ROOM[3]), mirror(ROOM[0]), mirror(ROOM[1]), 6, 9));
        return jobs;
    }

    private static TerrainJob river(int[] r, int fords) {
        return TerrainJob.crossings(TerrainJob.RIVER, r[0], r[1], r[2], r[3], fords, 4);
    }

    /** The terrain as block classes, as the mod's table classifies these blocks: for L1. */
    public VoxelWorld voxels() {
        VoxelWorld world = new VoxelWorld(SIZE, HEIGHT, SIZE);
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
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

    /**
     * The checker's reading of this map: the surfaces for every mobility class, the ground, the terrain's logs and
     * bushes with {@code extra} (the scenario's deposits and animals), the dropsite sites, the jobs.
     */
    public MapChecker.Input checkerInput(List<MapChecker.Site> extra) {
        VoxelWorld world = voxels();
        Map<MobilityClass, SurfaceGrid> grids = new EnumMap<>(MobilityClass.class);
        for (MobilityClass mobility : MobilityClass.values()) {
            grids.put(mobility, world.grid(mobility, NavOverlay.EMPTY));
        }
        List<MapChecker.Site> resources = new ArrayList<>(extra);
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                int logs = 0;
                for (int y = groundTop(x, z) + 1; y <= top(x, z); y++) {
                    byte b = block(x, y, z);
                    if (b == LOG) {
                        logs++;
                    } else if (b == BUSH) {
                        resources.add(new MapChecker.Site("food", x, z, BUSH_FOOD));
                    }
                }
                if (logs > 0) {
                    resources.add(new MapChecker.Site("wood", x, z, (double) logs * LOG_WOOD));
                }
            }
        }
        List<Map<String, MapWaypoint>> dropsites = new ArrayList<>();
        for (int k = 0; k < 2; k++) {
            Map<String, MapWaypoint> sites = new TreeMap<>();
            for (String kind : List.of("wood", "stone", "iron", "food")) {
                sites.put(kind, waypoints.get("dropsite_" + kind + "_" + k));
            }
            dropsites.add(sites);
        }
        MapChecker.Columns columns = new MapChecker.Columns() {
            @Override
            public int groundTop(int x, int z) {
                return FirstSkirmish.this.groundTop(x, z);
            }

            @Override
            public boolean clear(int x, int z) {
                return top(x, z) == groundTop(x, z);
            }
        };
        return new MapChecker.Input(startSlots, grids, columns, resources, dropsites, terrainJobs());
    }

    private static short classOf(byte b) {
        return switch (b) {
            case WATER -> VoxelFixtures.WATER;
            case LEAVES -> VoxelFixtures.LEAVES;
            case BUSH -> VoxelFixtures.BUSH;
            case AIR -> VoxelFixtures.AIR;
            default -> VoxelFixtures.SOLID;
        };
    }

    private static @Nullable Material materialOf(byte b) {
        return switch (b) {
            case LOG -> ProvingGround.LOG_MATERIAL;
            case LEAVES -> ProvingGround.LEAF_MATERIAL;
            case BUSH -> ProvingGround.BUSH_MATERIAL;
            default -> null;
        };
    }

    /** The ground's top: symmetric low hills, the start pads level at 15, the island's hill, the banks of the water. */
    private static int height(int x, int z) {
        int mx = mirror(x);
        int mz = mirror(z);
        double hills = 0.9 * (DetMath.sin(2 * Math.PI * x / 89 + 0.4) + DetMath.sin(2 * Math.PI * mx / 89 + 0.4))
                + 0.9 * (DetMath.sin(2 * Math.PI * z / 67 + 1.1) + DetMath.sin(2 * Math.PI * mz / 67 + 1.1))
                + 0.6
                        * (DetMath.sin(2 * Math.PI * (x + z) / 53 + 2.1)
                                + DetMath.sin(2 * Math.PI * (mx + mz) / 53 + 2.1));
        int g = BASE + (int) Math.round(hills);
        g = level(g, BASE, distance(x, z, PAD[0], PAD[1], PAD[2], PAD[3]));
        g = level(g, BASE, distance(x, z, mirror(PAD[2]), mirror(PAD[3]), mirror(PAD[0]), mirror(PAD[1])));
        if (inside(x, z, ISLAND)) {
            g = Math.max(13, HILL_TOP - distance(x, z, PLATEAU[0], PLATEAU[1], PLATEAU[2], PLATEAU[3]));
        }
        int fromWater = waterDistance(x, z);
        return Math.max(WATER_TOP, Math.min(g, WATER_TOP + Math.floorDiv(fromWater, 2)));
    }

    /** Water two deep (bed at 9) in every rectangle, one deep (bed at 10) at the fords. */
    private void river() {
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                if (waterDistance(x, z) != 0) {
                    continue;
                }
                int bed = ford(x, z) ? 10 : 9;
                for (int y = 0; y < HEIGHT; y++) {
                    set(x, y, z, y < bed ? STONE : y == bed ? SAND : y <= WATER_TOP ? WATER : AIR);
                }
                ground[x * SIZE + z] = bed;
            }
        }
    }

    private static boolean ford(int x, int z) {
        boolean west = x >= 160 && x <= 167 && z >= FORD_Z0 && z <= FORD_Z1;
        boolean east = x >= mirror(167) && x <= mirror(160) && z >= mirror(FORD_Z1) && z <= mirror(FORD_Z0);
        return west || east;
    }

    /**
     * A forest line in each half, five wide, the whole height of the map except its three gaps: oaks on the edge
     * that faces its start, one every 3 blocks, each a single trunk of four logs that workers fell from that side;
     * leaves three high over the rest, an obstacle that is never a floor, so the line is crossed only at its gaps; a
     * canopy two leaves deep over it. Separate trunks keep each tree its own node, and none stands on the river's side:
     * a computer that picks the tree nearest in a straight line would walk round through a gap to reach it. The east
     * line is the west's mirror.
     */
    private void forestLines() {
        for (int z = 0; z < SIZE; z++) {
            if (gap(z)) {
                continue;
            }
            for (int x = FOREST_X0; x <= FOREST_X1; x++) {
                boolean trunk = x == FOREST_X0 && z % 3 == 0;
                forestColumn(x, z, trunk);
                forestColumn(mirror(x), mirror(z), trunk);
            }
        }
        for (int z = 0; z < SIZE; z++) {
            if (gap(z)) {
                continue;
            }
            for (int x = FOREST_X0 - 1; x <= FOREST_X1 + 1; x++) {
                canopy(x, z);
                canopy(mirror(x), mirror(z));
            }
        }
    }

    private static boolean gap(int z) {
        for (int[] g : FOREST_GAPS) {
            if (z >= g[0] && z <= g[1]) {
                return true;
            }
        }
        return false;
    }

    private void forestColumn(int x, int z, boolean trunk) {
        int g = groundTop(x, z);
        for (int y = g + 1; y <= g + (trunk ? 4 : 3); y++) {
            set(x, y, z, trunk ? LOG : LEAVES);
        }
    }

    private void canopy(int x, int z) {
        int g = groundTop(x, z);
        for (int y = g + 5; y <= g + 6; y++) {
            if (block(x, y, z) == AIR) {
                set(x, y, z, LEAVES);
            }
        }
    }

    /**
     * Near each start two groves of nine oaks, 6 apart, north-east and south-west of it, and eight berry bushes south
     * of the civic centre; all within 48 blocks, clear of the scenario's deposits and animals and of the fields' room.
     */
    private void groves() {
        for (int cx = 0; cx < 3; cx++) {
            for (int cz = 0; cz < 3; cz++) {
                int trunk = 4 + (cx + cz) % 2;
                oak(60 + cx * 6, 108 + cz * 6, trunk);
                oak(mirror(60 + cx * 6), mirror(108 + cz * 6), trunk);
                oak(22 + cx * 6, 154 + cz * 6, trunk);
                oak(mirror(22 + cx * 6), mirror(154 + cz * 6), trunk);
            }
        }
        for (int x : new int[] {58, 61}) {
            for (int z : new int[] {146, 149, 152, 155}) {
                bush(x, z);
                bush(mirror(x), mirror(z));
            }
        }
    }

    private void bush(int x, int z) {
        set(x, groundTop(x, z) + 1, z, BUSH);
    }

    /** An oak as the proving ground's: a trunk on the ground, a 5 x 5 canopy two deep without its corners, 3 x 3, a cross. */
    private void oak(int x, int z, int trunk) {
        int g = groundTop(x, z);
        int crown = g + trunk;
        for (int y = g + 1; y <= crown; y++) {
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
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                int g = groundTop(x, z);
                if (block(x, g, z) == GRASS && block(x, g + 1, z) != AIR) {
                    set(x, g, z, DIRT);
                }
            }
        }
    }

    private static int waterDistance(int x, int z) {
        int best = Integer.MAX_VALUE;
        for (int[] r : WATER_RECTS) {
            best = Math.min(best, distance(x, z, r[0], r[1], r[2], r[3]));
        }
        return best;
    }

    private static boolean inside(int x, int z, int[] r) {
        return x >= r[0] && x <= r[2] && z >= r[1] && z <= r[3];
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

    private void pair(String name, int x, int z) {
        if (name.equals("start")) {
            waypoints.put("west", new MapWaypoint(x, floor(x, z), z));
            waypoints.put("east", new MapWaypoint(mirror(x), floor(mirror(x), mirror(z)), mirror(z)));
            return;
        }
        waypoints.put(name + "_0", new MapWaypoint(x, floor(x, z), z));
        waypoints.put(name + "_1", new MapWaypoint(mirror(x), floor(mirror(x), mirror(z)), mirror(z)));
    }

    /** The block a unit stands in at a column: above its top. */
    private int floor(int x, int z) {
        return top(x, z) + 1;
    }

    private void set(int x, int y, int z, byte b) {
        blocks[(x * SIZE + z) * HEIGHT + y] = b;
    }
}
