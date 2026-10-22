package dev.strategos.gametest.suites;

import dev.strategos.core.content.MapManifest;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.core.sim.world.WritePriority;
import dev.strategos.core.sim.world.WriteSite;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.GameTestTickets;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.content.MapPackage;
import dev.strategos.mod.server.content.MapPackageLoader;
import dev.strategos.mod.server.match.MapExporter;
import dev.strategos.mod.server.match.MapImporter;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSpace;
import dev.strategos.mod.server.world.BlockWriter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The map batch (spec §7.4, MAP-01), in {@code runGameTestSlow}: each test alone in its batch, importing far from its
 * own structure, in air, and holding its own tickets there. The batches' structures stand a few dozen blocks apart, and
 * an import never clears air, so each test imports at its own multiple of 2,048 blocks east: what one left never lies
 * in another's area. Imports are wall-clock work on the IO and light threads, and the GameTest server ticks back to
 * back, so the timeouts are generous in ticks.
 */
public final class MapTests {
    static final ResourceLocation PROVING_GROUND = Ids.id("proving_ground");
    private static final int FAR = 2_048;
    private static final int IMPORT_LANE = 1;
    private static final int TWICE_LANE = 2;
    private static final int ROUNDTRIP_LANE = 3;

    private MapTests() {}

    /**
     * MAP-01.1: a match on the proving ground imports and prepares within 30 s of wall time, and what stands in the level
     * hashes to the manifest's terrain hash.
     */
    @StrategosTest(batch = "map_import", solo = true, timeoutTicks = 40_000)
    public static void provingGroundImportsWithin30s(GameTestHelper h) {
        MatchRuntime rt = provingGround(h, IMPORT_LANE, 0);
        String expected = manifest(h).terrainHash();
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(rt.lifecycle() != MatchLifecycle.PREPARING, "importing"))
                .thenExecute(() -> {
                    h.assertTrue(
                            rt.lifecycle() == MatchLifecycle.RUNNING,
                            "the match is " + rt.lifecycle() + ": " + rt.faultMessage());
                    h.assertTrue(expected.equals(rt.importedHash()), "terrain " + rt.importedHash());
                    h.assertTrue(rt.importMillis() <= 30_000, "the import took " + rt.importMillis() + " ms");
                })
                .thenSucceed();
    }

    /**
     * MAP-01.2: two imports of the same package, outside any match, give equal terrain hashes. They are compared with
     * each other only: a tile that disagrees with the manifest still imports the same twice, and the manifest's hash is
     * {@link #provingGroundImportsWithin30s}'s to check.
     */
    @StrategosTest(batch = "map_twice", solo = true, timeoutTicks = 60_000)
    public static void twoImportsEqualHashes(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        MapPackage pkg = MapPackageLoader.load(level.getServer(), PROVING_GROUND);
        BlockPos corner = h.absolutePos(BlockPos.ZERO);
        List<ChunkPos> chunks = new ArrayList<>();
        List<MapImporter> imports = new ArrayList<>();
        for (int dz : new int[] {0, 512}) {
            BlockPos origin = new BlockPos(corner.getX() + TWICE_LANE * FAR, corner.getY(), corner.getZ() + dz);
            for (int x = origin.getX() >> 4;
                    x <= (origin.getX() + pkg.manifest().sizeX() - 1) >> 4;
                    x++) {
                for (int z = origin.getZ() >> 4;
                        z <= (origin.getZ() + pkg.manifest().sizeZ() - 1) >> 4;
                        z++) {
                    chunks.add(new ChunkPos(x, z));
                    GameTestTickets.force(level, new ChunkPos(x, z));
                }
            }
            imports.add(StrategosServer.get()
                    .mapImports()
                    .start(new MapImporter(
                            level, pkg, origin, 0, StrategosServer.get().writer())));
        }
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(
                        imports.get(0).hash() != null && imports.get(1).hash() != null, "importing"))
                .thenExecute(() -> {
                    chunks.forEach(c -> GameTestTickets.release(level, c));
                    h.assertTrue(
                            String.valueOf(imports.get(0).hash())
                                    .equals(imports.get(1).hash()),
                            "terrain " + imports.get(0).hash() + " and "
                                    + imports.get(1).hash());
                })
                .thenSucceed();
    }

    /**
     * MAP-01.2: a region written through the block writer, exported as a package and imported elsewhere, round-trips:
     * the import hashes to the export's terrain hash, and block for block the two regions match.
     */
    @StrategosTest(batch = "map_roundtrip", solo = true, timeoutTicks = 20_000)
    public static void exportImportRoundTrip(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockWriter writer = StrategosServer.get().writer();
        BlockPos from = h.absolutePos(new BlockPos(4, 1, 4));
        BlockPos to = from.offset(15, 7, 15);
        BlockPos target = from.offset(ROUNDTRIP_LANE * FAR, 0, 0);
        List<ChunkPos> chunks = new ArrayList<>();
        for (int x = target.getX() >> 4; x <= (target.getX() + 15) >> 4; x++) {
            for (int z = target.getZ() >> 4; z <= (target.getZ() + 15) >> 4; z++) {
                chunks.add(new ChunkPos(x, z));
                GameTestTickets.force(level, new ChunkPos(x, z));
            }
        }
        WriteSite site = new WriteSite(0, 7);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                writer.enqueue(level, site, WritePriority.CONSTRUCTION, from.offset(x, 0, z), stone(), 0);
                if (x == 0 || x == 15 || z == 0 || z == 15) {
                    for (int y = 1; y <= 3; y++) {
                        writer.enqueue(level, site, WritePriority.CONSTRUCTION, from.offset(x, y, z), planks(), 0);
                    }
                } else if (z == 8 && x > 1 && x < 14) {
                    writer.enqueue(level, site, WritePriority.CONSTRUCTION, from.offset(x, 1, z), fence(x), 0);
                } else if ((x + z) % 5 == 0) {
                    writer.enqueue(
                            level,
                            site,
                            WritePriority.CONSTRUCTION,
                            from.offset(x, 4, z),
                            Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true),
                            0);
                }
            }
        }
        var state = new Object() {
            MapManifest exported;
            MapImporter importer;
        };
        ResourceLocation id = Ids.id("roundtrip_" + Math.abs(from.getX() % 10_000));
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(writer.pending(level) == 0, "writing the region"))
                .thenIdle(10) // the shape pass's scheduled ticks settle the leaves before the export
                .thenExecute(() -> {
                    state.exported = MapExporter.export(level, id, from, to);
                    MapPackage pkg = MapPackageLoader.load(level.getServer(), id);
                    state.importer =
                            StrategosServer.get().mapImports().start(new MapImporter(level, pkg, target, 0, writer));
                })
                .thenWaitUntil(() -> h.assertTrue(state.importer.hash() != null, "importing"))
                .thenExecute(() -> {
                    try {
                        h.assertTrue(
                                state.exported.terrainHash().equals(state.importer.hash()),
                                "exported " + state.exported.terrainHash() + ", imported " + state.importer.hash());
                        for (int x = 0; x < 16; x++) {
                            for (int y = 0; y < 8; y++) {
                                for (int z = 0; z < 16; z++) {
                                    BlockState a = level.getBlockState(from.offset(x, y, z));
                                    BlockState b = level.getBlockState(target.offset(x, y, z));
                                    h.assertTrue(a.equals(b), "at " + x + ", " + y + ", " + z + ": " + a + " vs " + b);
                                }
                            }
                        }
                    } finally {
                        chunks.forEach(c -> GameTestTickets.release(level, c));
                    }
                })
                .thenSucceed();
    }

    /** A proving-ground match in the test's lane east of it, {@code dz} blocks along Z, its band from the test's floor. */
    private static MatchRuntime provingGround(GameTestHelper h, int lane, int dz) {
        MapManifest m = manifest(h);
        BlockPos corner = h.absolutePos(BlockPos.ZERO);
        BlockPos origin = new BlockPos(corner.getX() + lane * FAR, corner.getY(), corner.getZ() + dz);
        MatchSpace space = new MatchSpace(
                h.getLevel().dimension(), origin, m.sizeX(), m.sizeZ(), origin.getY(), origin.getY() + m.height() - 1);
        MatchOptions options = MatchFixture.options(SimHarness.DEFAULT_SEED, h)
                .withMap(PROVING_GROUND, origin.getY() + m.startSlots().get(0).y());
        return MatchFixture.create(h, space, options);
    }

    private static MapManifest manifest(GameTestHelper h) {
        return MapPackageLoader.load(h.getLevel().getServer(), PROVING_GROUND).manifest();
    }

    private static BlockState stone() {
        return Blocks.STONE.defaultBlockState();
    }

    private static BlockState planks() {
        return Blocks.OAK_PLANKS.defaultBlockState();
    }

    private static BlockState fence(int x) {
        return Blocks.OAK_FENCE
                .defaultBlockState()
                .setValue(FenceBlock.WEST, x > 2)
                .setValue(FenceBlock.EAST, x < 13);
    }
}
