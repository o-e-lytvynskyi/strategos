package dev.strategos.gametest.spike;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import dev.strategos.core.sim.nav.BlockClass;
import dev.strategos.core.sim.nav.MobilityClass;
import dev.strategos.core.sim.nav.NavOverlay;
import dev.strategos.core.sim.nav.PageBuilder;
import dev.strategos.core.sim.nav.PageCoord;
import dev.strategos.core.sim.nav.Surface;
import dev.strategos.core.sim.nav.SurfacePage;
import dev.strategos.core.sim.nav.VoxelSlice;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.world.BlockClassTable;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spike S8 (spec §8.1, PATH-01): a census of surfaces per column on vanilla terrain. For each biome of {@code
 * census/s8-regions.json}, the 512² square around the nearest place of that biome (ServerLevel#findClosestBiome3d,
 * ServerLevel.java:1348) is generated, classified through the production {@link BlockClassTable} over 128-block bands
 * and built page by page with {@link PageBuilder}; then the L1 fixtures of a bridge, a wall walkway and a tower. Writes
 * {@code census.csv}, {@code fixtures.csv}, {@code env.json} and {@code DONE} to {@code out/s8}.
 *
 * <p>Each square is measured under three {@link BandRule}s. Besides what the page keeps, each column's surfaces are
 * counted without the cap of 4, with {@code PageBuilder}'s rule, and checked against the page. Counted apart: surfaces
 * no deeper than 16 blocks below the column's terrain (its top motion-blocking block that is not leaves); columns that
 * would lose a surface no deeper than 2 blocks below it, which is ground a unit stands on; and columns whose terrain
 * lies outside the band. Squares may overlap, so each rule also gets a row over the distinct chunks of all squares.
 *
 * <p>Chunks load through region tickets of the census's own type, at most {@link #IN_FLIGHT} at a time, and the census
 * polls for them each tick, giving a square up after two minutes without progress (the spike server sprints, so ticks
 * say nothing about time): NeoForge's {@code TicketController#forceChunk} generates synchronously on the server thread
 * (ForcedChunkManager.java:96), and forcing a whole square that way hung the server.
 */
final class S8Census implements Spike {
    private static final Logger LOG = LoggerFactory.getLogger(S8Census.class);
    private static final TicketType<ChunkPos> TICKET =
            TicketType.create("strategos_s8", Comparator.comparingLong(ChunkPos::toLong));
    private static final int REGION_CHUNKS = 32;
    private static final int CHUNKS = REGION_CHUNKS * REGION_CHUNKS;
    private static final int IN_FLIGHT = 128;
    private static final long STALL_NANOS = 120_000_000_000L;
    private static final int BAND = 128;
    private static final int BELOW = 16;
    private static final int NEAR = 16 * 16;
    private static final int GROUND = 2 * 16;
    private static final int HISTOGRAM = 8;
    private static final int WARM_UP = 2_000;
    private static final String HEADER = "biome,status,x,z,rule,band_min_y,terrain_min_y,terrain_p1_y,terrain_p50_y,"
            + "terrain_p99_y,terrain_max_y,columns,cols_0,cols_1,cols_2,cols_3,cols_4,cols_5,cols_6,cols_7_plus,"
            + "share_within_4,share_within_6,near_columns_over_4,share_near_within_4,ground_dropped_columns,"
            + "above_band_columns,below_band_columns,page_bytes_mean,page_bytes_max,nav_bytes,build_us_p50,"
            + "build_us_p95,load_ms";

    private final ServerLevel level;
    private final long seed;
    private final List<String> biomes = new ArrayList<>();
    private final int fromX;
    private final int fromZ;
    private final List<String> rows = new ArrayList<>();
    private final List<String> fixtureRows = new ArrayList<>();
    private final PageBuilder builder = new PageBuilder(MobilityClass.INFANTRY);
    private final int[] floors = new int[VoxelSlice.MAX_HEIGHT];
    private final short[][] voxelBuffers = new short[BAND + 1][];
    private final Map<BandRule, Tally> distinct = new EnumMap<>(BandRule.class);
    private final LongOpenHashSet seen = new LongOpenHashSet();
    private int regionIndex;
    private @Nullable Region region;

    /** Where a page's band starts: {@link #BELOW} blocks under some terrain height. */
    private enum BandRule {
        /** The square's lowest terrain (spec §8.1). */
        SQUARE_MIN,
        /** The square's 1st percentile of terrain heights: the deepest pits fall out of the band. */
        SQUARE_P1,
        /** The page's own lowest terrain. */
        PAGE_MIN
    }

    /** One biome's square while it loads. */
    private static final class Region {
        final String biome;
        final BlockPos found;
        final int cx0;
        final int cz0;
        final boolean[] loaded = new boolean[CHUNKS];
        final long startNanos = System.nanoTime();
        long lastProgress = startNanos;
        int requested;
        int done;

        Region(String biome, BlockPos found) {
            this.biome = biome;
            this.found = found;
            this.cx0 = (found.getX() >> 4) - REGION_CHUNKS / 2;
            this.cz0 = (found.getZ() >> 4) - REGION_CHUNKS / 2;
        }

        ChunkPos pos(int i) {
            return new ChunkPos(cx0 + i % REGION_CHUNKS, cz0 + i / REGION_CHUNKS);
        }
    }

    /** Surfaces per column, without the cap, against what the pages kept. */
    private static final class Tally {
        final long[] byCount = new long[HISTOGRAM];
        long columns;
        long overflowColumns;
        long withinSix;
        long nearOver;
        long groundDropped;
        long aboveBand;
        long belowBand;
        long bytes;
        int bytesMax;
        int pages;

        void merge(Tally o) {
            for (int i = 0; i < HISTOGRAM; i++) {
                byCount[i] += o.byCount[i];
            }
            columns += o.columns;
            overflowColumns += o.overflowColumns;
            withinSix += o.withinSix;
            nearOver += o.nearOver;
            groundDropped += o.groundDropped;
            aboveBand += o.aboveBand;
            belowBand += o.belowBand;
            bytes += o.bytes;
            bytesMax = Math.max(bytesMax, o.bytesMax);
            pages += o.pages;
        }

        /** From {@code columns} to {@code nav_bytes} of {@link #HEADER}. */
        String csv() {
            StringBuilder sb = new StringBuilder().append(columns);
            for (long count : byCount) {
                sb.append(',').append(count);
            }
            return sb.append(',')
                    .append(share(columns - overflowColumns, columns))
                    .append(',')
                    .append(share(withinSix, columns))
                    .append(',')
                    .append(nearOver)
                    .append(',')
                    .append(share(columns - nearOver, columns))
                    .append(',')
                    .append(groundDropped)
                    .append(',')
                    .append(aboveBand)
                    .append(',')
                    .append(belowBand)
                    .append(',')
                    .append(pages == 0 ? 0 : bytes / pages)
                    .append(',')
                    .append(bytesMax)
                    .append(',')
                    .append(bytes)
                    .toString();
        }
    }

    S8Census(MinecraftServer server) {
        this.level = server.overworld();
        JsonObject plan = readPlan();
        this.seed = plan.get("seed").getAsLong();
        this.fromX = plan.getAsJsonArray("searchFrom").get(0).getAsInt();
        this.fromZ = plan.getAsJsonArray("searchFrom").get(1).getAsInt();
        for (var biome : plan.getAsJsonArray("biomes")) {
            biomes.add(biome.getAsString());
        }
        long worldSeed = server.getWorldData().worldGenOptions().seed();
        if (worldSeed != seed) {
            throw new IllegalStateException("the census plan's seed is " + seed + ", the world's " + worldSeed);
        }
        warmUp();
    }

    @Override
    public void preTick() {}

    @Override
    public boolean postTick() {
        if (regionIndex >= biomes.size()) {
            fixtures();
            return true;
        }
        Region r = region;
        if (r == null) {
            String biome = biomes.get(regionIndex);
            ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, Ids.parse(biome));
            Pair<BlockPos, Holder<Biome>> found =
                    level.findClosestBiome3d(h -> h.is(key), new BlockPos(fromX, 64, fromZ), 6_400, 32, 64);
            if (found == null) {
                rows.add(biome + ",not found" + ",".repeat(HEADER.split(",").length - 2));
                regionIndex++;
                return false;
            }
            region = new Region(biome, found.getFirst());
            LOG.info("S8: {} at {}", biome, found.getFirst());
            return false;
        }
        ServerChunkCache cache = level.getChunkSource();
        for (int i = 0; i < r.requested; i++) {
            if (!r.loaded[i]) {
                ChunkPos pos = r.pos(i);
                if (cache.getChunkNow(pos.x, pos.z) != null) {
                    r.loaded[i] = true;
                    r.done++;
                    r.lastProgress = System.nanoTime();
                }
            }
        }
        while (r.requested < CHUNKS && r.requested - r.done < IN_FLIGHT) {
            ChunkPos pos = r.pos(r.requested++);
            cache.addRegionTicket(TICKET, pos, 0, pos);
        }
        if (r.done == CHUNKS) {
            measure(r);
            release(r);
        } else if (System.nanoTime() - r.lastProgress > STALL_NANOS) {
            LOG.warn("S8: {} stalled with {} of {} chunks loaded", r.biome, r.done, CHUNKS);
            rows.add(r.biome + ",stalled at " + r.done + " of " + CHUNKS + ",".repeat(HEADER.split(",").length - 2));
            release(r);
        }
        return false;
    }

    private void release(Region r) {
        for (int i = 0; i < r.requested; i++) {
            ChunkPos pos = r.pos(i);
            level.getChunkSource().removeRegionTicket(TICKET, pos, 0, pos);
        }
        region = null;
        regionIndex++;
    }

    private void measure(Region r) {
        long loadMillis = (System.nanoTime() - r.startNanos) / 1_000_000;
        int minY = level.getMinBuildHeight();
        LevelChunk[] chunks = new LevelChunk[CHUNKS];
        int[][] terrain = new int[CHUNKS][SurfacePage.COLUMNS];
        long[] heights = new long[level.getHeight() + 1];
        for (int i = 0; i < CHUNKS; i++) {
            ChunkPos pos = r.pos(i);
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk == null) {
                throw new IllegalStateException("chunk " + pos + " unloaded before the census read it");
            }
            chunks[i] = chunk;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int h = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    terrain[i][SurfacePage.column(x, z)] = h;
                    heights[h - minY + 1]++;
                }
            }
        }
        int lowest = percentile(heights, 0);
        int p1 = percentile(heights, 0.01);
        String range = String.join(
                ",",
                String.valueOf(lowest),
                String.valueOf(p1),
                String.valueOf(percentile(heights, 0.5)),
                String.valueOf(percentile(heights, 0.99)),
                String.valueOf(percentile(heights, 1)));
        for (BandRule rule : BandRule.values()) {
            Tally tally = new Tally();
            Tally all = distinct.computeIfAbsent(rule, k -> new Tally());
            long[] buildNanos = new long[CHUNKS];
            for (int i = 0; i < CHUNKS; i++) {
                int from = switch (rule) {
                    case SQUARE_MIN -> lowest;
                    case SQUARE_P1 -> p1;
                    case PAGE_MIN -> Arrays.stream(terrain[i]).min().orElseThrow();
                };
                int bandMin = Math.max(minY, from - BELOW);
                int bandTop = Math.min(level.getMaxBuildHeight(), bandMin + BAND);
                VoxelSlice slice = slice(chunks[i], bandMin, bandTop);
                PageCoord coord = new PageCoord(i % REGION_CHUNKS, i / REGION_CHUNKS);
                long t0 = System.nanoTime();
                SurfacePage page = builder.build(slice, NavOverlay.EMPTY, coord);
                buildNanos[i] = System.nanoTime() - t0;
                Tally pageTally = new Tally();
                add(pageTally, slice, page, terrain[i], bandMin, bandTop);
                tally.merge(pageTally);
                if (!seen.contains(r.pos(i).toLong())) {
                    all.merge(pageTally);
                }
            }
            Arrays.sort(buildNanos);
            String bandMin = rule == BandRule.PAGE_MIN
                    ? "per_page"
                    : String.valueOf(Math.max(minY, (rule == BandRule.SQUARE_MIN ? lowest : p1) - BELOW));
            rows.add(String.join(
                    ",",
                    r.biome,
                    "found",
                    String.valueOf(r.found.getX()),
                    String.valueOf(r.found.getZ()),
                    rule.name().toLowerCase(Locale.ROOT),
                    bandMin,
                    range,
                    tally.csv(),
                    micros(buildNanos[buildNanos.length / 2]),
                    micros(buildNanos[(int) (buildNanos.length * 0.95)]),
                    String.valueOf(loadMillis)));
            LOG.info(
                    "S8: {} {}: {} of {} columns within four surfaces, {} over four near the terrain, {} lose ground,"
                            + " {} above and {} below the band",
                    r.biome,
                    rule.name().toLowerCase(Locale.ROOT),
                    tally.columns - tally.overflowColumns,
                    tally.columns,
                    tally.nearOver,
                    tally.groundDropped,
                    tally.aboveBand,
                    tally.belowBand);
        }
        for (int i = 0; i < CHUNKS; i++) {
            seen.add(r.pos(i).toLong());
        }
    }

    /** The lowest terrain height at or below which a share {@code q} of the square's columns lie. */
    private int percentile(long[] heights, double q) {
        long total = 0;
        for (long count : heights) {
            total += count;
        }
        long target = Math.max(1, (long) Math.ceil(q * total));
        long cumulative = 0;
        for (int i = 0; i < heights.length; i++) {
            cumulative += heights[i];
            if (cumulative >= target) {
                return i + level.getMinBuildHeight() - 1;
            }
        }
        return level.getMaxBuildHeight() - 1;
    }

    /** One chunk's classes between {@code bandMin} and {@code bandTop}, through the production table. */
    private VoxelSlice slice(LevelChunk chunk, int bandMin, int bandTop) {
        BlockClassTable table = StrategosServer.get().blockClasses();
        int height = bandTop - bandMin;
        short[] voxels = voxelBuffers[height];
        if (voxels == null) {
            voxels = new short[VoxelSlice.SIDE * VoxelSlice.SIDE * height];
            voxelBuffers[height] = voxels;
        }
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < height; y++) {
                    int worldY = bandMin + y;
                    LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(worldY));
                    voxels[(x * 16 + z) * height + y] = section.hasOnlyAir()
                            ? BlockClass.AIR
                            : table.classOf(section.getBlockState(x, worldY & 15, z));
                }
            }
        }
        return new VoxelSlice(height, voxels);
    }

    /**
     * Counts one page's columns. Each column's surfaces are found again without the cap, and the topmost four must be
     * the page's own; {@code terrain}, per column, is the world height of the terrain's top block, or null for a
     * fixture.
     */
    private void add(
            Tally tally, VoxelSlice slice, SurfacePage page, int @Nullable [] terrain, int bandMin, int bandTop) {
        long overflowColumns = 0;
        for (int x = 0; x < VoxelSlice.SIDE; x++) {
            for (int z = 0; z < VoxelSlice.SIDE; z++) {
                int column = SurfacePage.column(x, z);
                int n = floors(slice, x, z);
                int kept = Math.min(n, SurfacePage.MAX_LAYERS);
                if (page.count(column) != kept) {
                    throw new IllegalStateException(
                            "column " + column + ": " + n + " surfaces, the page kept " + page.count(column));
                }
                for (int k = 0; k < kept; k++) {
                    if (Surface.floor(page.surface(column, k)) != floors[k]) {
                        throw new IllegalStateException("column " + column + ": surface " + k + " differs");
                    }
                }
                tally.byCount[Math.min(n, HISTOGRAM - 1)]++;
                if (n > SurfacePage.MAX_LAYERS) {
                    overflowColumns++;
                }
                if (n <= 6) {
                    tally.withinSix++;
                }
                if (terrain != null) {
                    int h = terrain[column];
                    if (h >= bandTop) {
                        tally.aboveBand++;
                    } else if (h < bandMin) {
                        tally.belowBand++;
                    }
                    int top = (h + 1 - bandMin) * 16;
                    int near = 0;
                    int ground = 0;
                    for (int k = 0; k < n; k++) {
                        if (floors[k] >= top - NEAR) {
                            near++;
                        }
                        if (floors[k] >= top - GROUND) {
                            ground++;
                        }
                    }
                    if (near > SurfacePage.MAX_LAYERS) {
                        tally.nearOver++;
                    }
                    if (ground > SurfacePage.MAX_LAYERS) {
                        tally.groundDropped++;
                    }
                }
            }
        }
        if (overflowColumns != page.overflowColumns()) {
            throw new IllegalStateException(
                    overflowColumns + " columns over four, the page counted " + page.overflowColumns());
        }
        tally.columns += SurfacePage.COLUMNS;
        tally.overflowColumns += overflowColumns;
        tally.bytes += page.bytes();
        tally.bytesMax = Math.max(tally.bytesMax, page.bytes());
        tally.pages++;
    }

    /** {@link PageBuilder}'s surface rule without the cap: the floors of a column's surfaces, topmost first. */
    private int floors(VoxelSlice slice, int x, int z) {
        int height = slice.height();
        int n = 0;
        for (int y = height - 1; y >= 0; y--) {
            short c = slice.get(x, y, z);
            if (!BlockClass.is(c, BlockClass.STANDABLE) || BlockClass.top(c) == 0) {
                continue;
            }
            int floor = y * 16 + BlockClass.top(c);
            int ceiling = (height + 4) * 16;
            for (int above = y + 1; above < height; above++) {
                if (!BlockClass.clear(slice.get(x, above, z))) {
                    ceiling = above * 16;
                    break;
                }
            }
            if (ceiling - floor >= MobilityClass.INFANTRY.clearance()) {
                floors[n++] = floor;
            }
        }
        return n;
    }

    /** Builds the fixtures until the JIT has compiled the builder, so the first square's times are not the JIT's. */
    private void warmUp() {
        List<VoxelSlice> slices = new ArrayList<>();
        for (VoxelWorld world : fixtureWorlds().values()) {
            slices.add(world.slice(new PageCoord(0, 0)));
        }
        for (int i = 0; i < WARM_UP; i++) {
            for (VoxelSlice slice : slices) {
                builder.build(slice, NavOverlay.EMPTY, new PageCoord(0, 0));
            }
        }
    }

    /** The L1 fixtures: a bridge over walkable ground, a wall walkway and a three-floor tower. */
    private void fixtures() {
        for (Map.Entry<String, VoxelWorld> e : fixtureWorlds().entrySet()) {
            PageCoord origin = new PageCoord(0, 0);
            VoxelSlice slice = e.getValue().slice(origin);
            SurfacePage page = builder.build(slice, NavOverlay.EMPTY, origin);
            Tally tally = new Tally();
            add(tally, slice, page, null, 0, slice.height());
            fixtureRows.add(e.getKey() + "," + tally.csv());
        }
    }

    private static Map<String, VoxelWorld> fixtureWorlds() {
        VoxelWorld bridge = VoxelFixtures.flat(16, 16, 32, 4);
        bridge.fill(0, 8, 6, 15, 8, 9, VoxelFixtures.SOLID);
        VoxelWorld wall = VoxelFixtures.flat(16, 16, 32, 4);
        wall.fill(0, 4, 7, 15, 9, 8, VoxelFixtures.SOLID);
        VoxelWorld tower = VoxelFixtures.flat(16, 16, 32, 4);
        tower.fill(4, 4, 4, 11, 16, 11, VoxelFixtures.SOLID);
        tower.fill(5, 4, 5, 10, 16, 10, VoxelFixtures.AIR);
        for (int floor : new int[] {8, 12}) {
            tower.fill(5, floor, 5, 10, floor, 10, VoxelFixtures.SOLID);
        }
        Map<String, VoxelWorld> worlds = new LinkedHashMap<>();
        worlds.put("bridge", bridge);
        worlds.put("wall_walkway", wall);
        worlds.put("tower", tower);
        return worlds;
    }

    private static String share(long part, long whole) {
        return String.format(Locale.ROOT, "%.6f", whole == 0 ? 0.0 : (double) part / whole);
    }

    private static String micros(long nanos) {
        return String.format(Locale.ROOT, "%.1f", nanos / 1000.0);
    }

    @Override
    public void finish(boolean complete) {
        Path dir = SpikeReport.dirFor("s8");
        List<String> all = new ArrayList<>(rows);
        for (Map.Entry<BandRule, Tally> e : distinct.entrySet()) {
            all.add("all,distinct,,," + e.getKey().name().toLowerCase(Locale.ROOT) + ",,,,,,,"
                    + e.getValue().csv() + ",,,");
        }
        try {
            Files.createDirectories(dir);
            Files.writeString(
                    dir.resolve("census.csv"), HEADER + "\n" + String.join("\n", all) + "\n", StandardCharsets.UTF_8);
            Files.writeString(
                    dir.resolve("fixtures.csv"),
                    "fixture,columns,cols_0,cols_1,cols_2,cols_3,cols_4,cols_5,cols_6,cols_7_plus,share_within_4,"
                            + "share_within_6,near_columns_over_4,share_near_within_4,ground_dropped_columns,"
                            + "above_band_columns,below_band_columns,page_bytes_mean,page_bytes_max,nav_bytes\n"
                            + String.join("\n", fixtureRows) + "\n",
                    StandardCharsets.UTF_8);
            JsonObject env = new JsonObject();
            env.addProperty("seed", seed);
            env.addProperty("java", System.getProperty("java.version"));
            env.addProperty("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
            env.addProperty("cores", Runtime.getRuntime().availableProcessors());
            env.addProperty("neoforge", System.getProperty("strategos.neoforge.version", "?"));
            env.addProperty("band", BAND);
            env.addProperty("bandBelowTerrain", BELOW);
            env.addProperty("nearSixteenths", NEAR);
            env.addProperty("groundSixteenths", GROUND);
            env.addProperty("chunksInFlight", IN_FLIGHT);
            env.addProperty("warmUpBuilds", WARM_UP * 3);
            Files.writeString(dir.resolve("env.json"), env + "\n", StandardCharsets.UTF_8);
            if (complete) {
                Files.writeString(dir.resolve("DONE"), "ok\n", StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JsonObject readPlan() {
        try (InputStream in = S8Census.class.getResourceAsStream("/census/s8-regions.json")) {
            if (in == null) {
                throw new IllegalStateException("no census/s8-regions.json on the classpath");
            }
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
