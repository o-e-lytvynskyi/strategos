package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;
import static dev.strategos.gametest.suites.Units.move;
import static dev.strategos.gametest.suites.Units.onlyUnit;
import static dev.strategos.gametest.suites.Units.spawn;
import static dev.strategos.gametest.suites.Units.unit;

import dev.strategos.core.content.MapManifest;
import dev.strategos.core.content.StartSlot;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.journal.ReplayDriver;
import dev.strategos.core.sim.journal.ReplayResult;
import dev.strategos.core.sim.nav.GridAStar;
import dev.strategos.core.sim.nav.PathService;
import dev.strategos.core.sim.nav.PathStatus;
import dev.strategos.core.sim.nav.Surface;
import dev.strategos.core.sim.nav.SurfaceGrid;
import dev.strategos.core.sim.world.WritePriority;
import dev.strategos.core.sim.world.WriteSite;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.GameTestTickets;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.entity.UnitEntity;
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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The map batch (spec §7.4, MAP-01), in {@code runGameTestSlow}: each test alone in its batch, importing far from its
 * own structure, in air, and holding its own tickets there. The batches' structures stand a few dozen blocks apart, and
 * an import never clears air, so each test imports at its own multiple of 2,048 blocks east: what one left never lies
 * in another's area. Imports are wall-clock work on the IO and light threads, and the GameTest server ticks back to
 * back, so the timeouts are generous in ticks.
 */
public final class MapTests {
    static final ResourceLocation PROVING_GROUND = Ids.id("proving_ground");
    private static final Logger LOG = LoggerFactory.getLogger(MapTests.class);
    private static final int FAR = 2_048;
    private static final int IMPORT_LANE = 1;
    private static final int TWICE_LANE = 2;
    private static final int ROUNDTRIP_LANE = 3;
    private static final int CROSS_LANE = 4;
    private static final int BODY_LANE = 5;
    private static final int FIFTY_LANE = 6;
    private static final int FIFTY = 50;
    private static final int CROWD_LANE = 7;
    private static final int CROWD = 200;
    private static final int SOAK_LANE = 8;
    private static final int SKIRMISH_LANE = 9;

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

    /**
     * Slice 1A's demo (PATH-05, spec §1.2): one unit walks the imported proving ground from its west start to its east
     * start, over its hills, steps and ramps and across the river; it arrives on the target, and the match's journal,
     * replayed in L1 from its start (the pages' slices included), reaches the live match's hash.
     */
    @StrategosTest(batch = "map_cross", solo = true, timeoutTicks = 60_000)
    public static void unitCrossesProvingGround(GameTestHelper h) {
        MatchRuntime rt = provingGround(h, CROSS_LANE, 0);
        crossing(h, rt, (unit, core) -> {}, core -> {
            ReplayResult replay = ReplayDriver.replay(core.journal(), TemplateSet.builtin());
            check(replay.matches(), "the journal does not replay: " + replay);
            check(replay.finalHash() == core.stateHash(), "the replay ends on another hash: " + replay);
        });
    }

    /**
     * PATH-05.2: along the same crossing, every tick the unit has a body, the body's Y is the core's Y within 1/16
     * block; and the core's Y did follow the terrain, more than two blocks up and down.
     */
    @StrategosTest(batch = "map_body", solo = true, timeoutTicks = 60_000)
    public static void bodyYFollowsCoreFloor(GameTestHelper h) {
        MatchRuntime rt = provingGround(h, BODY_LANE, 0);
        var seen = new Object() {
            int bodies;
            double low = Double.POSITIVE_INFINITY;
            double high = Double.NEGATIVE_INFINITY;
        };
        crossing(
                h,
                rt,
                (unit, core) -> {
                    UnitEntity body = rt.bodies().get(unit.handle());
                    seen.low = Math.min(seen.low, unit.y());
                    seen.high = Math.max(seen.high, unit.y());
                    if (body != null) {
                        seen.bodies++;
                        check(
                                Math.abs(body.getY() - unit.y()) <= 1 / 16.0,
                                "body Y " + body.getY() + ", core Y " + unit.y() + " at tick "
                                        + core.queries().tick());
                    }
                },
                core -> {
                    check(seen.bodies > 500, "the unit had a body on " + seen.bodies + " ticks");
                    check(seen.high - seen.low > 2, "the walk stayed between Y " + seen.low + " and " + seen.high);
                });
    }

    /**
     * PATH-05.3 (spec §1.6 B row 5): 50 units spawned at the west start are ordered one by one, ten ticks apart, each
     * to its own point by the east start. Each arrives within 1.5 times its ideal time, the time a walker at its speed
     * needs along a cheapest path (slowed in water and on slow ground), plus ten ticks for the request; and no unit
     * goes 100 ticks without progress.
     */
    @StrategosTest(batch = "map_fifty", solo = true, timeoutTicks = 120_000)
    public static void fiftyUnitsOneByOne(GameTestHelper h) {
        MatchRuntime rt = provingGround(h, FIFTY_LANE, 0);
        MapManifest m = manifest(h);
        StartSlot west = m.startSlots().get(0);
        StartSlot east = m.startSlots().get(1);
        var state = new Object() {
            boolean spawned;
            int[] units;
            int ordered;
            long nextOrder;
            final long[] orderedAt = new long[FIFTY];
            final double[] ideal = new double[FIFTY];
            final long[] arrivedAt = new long[FIFTY];
            int arrived;
            int mostStuck;
            String worst = "";
            double slowest; // the largest ratio of a unit's time to its ideal
        };
        h.onEachTick(() -> {
            if (rt.lifecycle() == MatchLifecycle.PREPARING) {
                return;
            }
            check(rt.lifecycle() == MatchLifecycle.RUNNING, "the match is " + rt.lifecycle());
            MatchCore core = core(rt);
            if (!state.spawned) {
                state.spawned = true;
                spawn(rt, west.x() + 0.5, west.z() + 0.5, FIFTY, 6);
                return;
            }
            if (state.units == null) {
                int[] handles = core.queries().unitHandles();
                if (handles.length < FIFTY) {
                    return;
                }
                state.units = handles;
                state.nextOrder = core.queries().tick();
            }
            long tick = core.queries().tick();
            if (state.ordered < FIFTY && tick >= state.nextOrder) {
                int k = state.ordered++;
                double tx = east.x() + 0.5 - (k % 5) * 2;
                double tz = east.z() + 0.5 + (k / 5 - 5) * 2;
                UnitView u = unit(rt, state.units[k]);
                state.ideal[k] = idealTicks(core, u, tx, tz);
                state.orderedAt[k] = tick;
                state.nextOrder = tick + 10;
                move(rt, state.units[k], tx, tz);
            }
            for (int k = 0; k < state.ordered; k++) {
                if (state.arrivedAt[k] != 0) {
                    continue;
                }
                MotionStore.Extra motion = core.queries().motion(state.units[k]);
                if (motion != null && motion.stuck() > state.mostStuck) {
                    state.mostStuck = motion.stuck();
                    UnitView stuck = unit(rt, state.units[k]);
                    state.worst = "unit " + k + " at " + stuck.x() + ", " + stuck.y() + ", " + stuck.z() + " aiming at "
                            + motion.aimX() + ", " + motion.aimZ() + ", path "
                            + core.queries().path(state.units[k]);
                }
                UnitView u = unit(rt, state.units[k]);
                if (u.state() == UnitState.IDLE && u.target() == null && tick > state.orderedAt[k] + 2) {
                    state.arrivedAt[k] = tick;
                    state.arrived++;
                    long took = tick - state.orderedAt[k];
                    state.slowest = Math.max(state.slowest, took / state.ideal[k]);
                    check(
                            took <= 1.5 * state.ideal[k] + 10,
                            "unit " + k + " took " + took + " ticks, ideal " + state.ideal[k]);
                }
            }
            check(state.mostStuck < 100, "a unit went " + state.mostStuck + " ticks without progress: " + state.worst);
            if (state.arrived == FIFTY) {
                LOG.info(
                        "fifty_units_one_by_one: the slowest unit took {} times its ideal time; at most {} ticks"
                                + " without progress",
                        String.format(Locale.ROOT, "%.2f", state.slowest),
                        state.mostStuck);
                h.succeed();
            }
        });
    }

    /**
     * Walks one unit from the proving ground's west start to its east start, calling {@code eachTick} every tick it is
     * on its way and {@code atTheEnd} once it stands on the target.
     */
    private static void crossing(
            GameTestHelper h, MatchRuntime rt, BiConsumer<UnitView, MatchCore> eachTick, Consumer<MatchCore> atTheEnd) {
        MapManifest m = manifest(h);
        StartSlot west = m.startSlots().get(0);
        StartSlot east = m.startSlots().get(1);
        double tx = east.x() + 0.5;
        double tz = east.z() + 0.5;
        var state = new Object() {
            boolean spawned;
            boolean ordered;
        };
        h.onEachTick(() -> {
            if (rt.lifecycle() == MatchLifecycle.PREPARING) {
                return;
            }
            check(rt.lifecycle() == MatchLifecycle.RUNNING, "the match is " + rt.lifecycle());
            MatchCore core = core(rt);
            if (!state.spawned) {
                state.spawned = true;
                spawn(rt, west.x() + 0.5, west.z() + 0.5, 1, 0);
                return;
            }
            if (core.queries().unitCount() == 0) {
                return;
            }
            int handle = onlyUnit(rt);
            if (!state.ordered) {
                state.ordered = true;
                move(rt, handle, tx, tz);
                return;
            }
            UnitView u = unit(rt, handle);
            eachTick.accept(u, core);
            if (u.state() == UnitState.IDLE && u.target() == null) {
                check(u.x() == tx && u.z() == tz, "stopped at " + u.x() + ", " + u.z());
                atTheEnd.accept(core);
                h.succeed();
            }
        });
    }

    /**
     * PATH-09.2: 200 units spawned round the west start, ordered as one group to the east start, all arrive at their
     * places within 1.5 times each one's ideal time (and 10 ticks), with no ladder rung above the repath: no unit goes
     * 100 ticks without progress. The group's paths are one request.
     */
    @StrategosTest(batch = "map_crowd", solo = true, timeoutTicks = 120_000)
    public static void twoHundredCross(GameTestHelper h) {
        MatchRuntime rt = provingGround(h, CROWD_LANE, 0);
        MapManifest m = manifest(h);
        StartSlot west = m.startSlots().get(0);
        StartSlot east = m.startSlots().get(1);
        var state = new Object() {
            boolean spawned;
            int[] units;
            long orderedAt = -1;
            double[] ideal;
            int arrived;
            boolean[] in;
            int mostStuck;
            String worst = "";
            double slowest;
        };
        h.onEachTick(() -> {
            if (rt.lifecycle() == MatchLifecycle.PREPARING) {
                return;
            }
            check(rt.lifecycle() == MatchLifecycle.RUNNING, "the match is " + rt.lifecycle());
            MatchCore core = core(rt);
            if (!state.spawned) {
                state.spawned = true;
                spawn(rt, west.x() + 0.5, west.z() + 0.5, CROWD, 12);
                return;
            }
            if (state.units == null) {
                int[] handles = core.queries().unitHandles();
                if (handles.length < CROWD) {
                    return;
                }
                state.units = handles;
                state.in = new boolean[CROWD];
                rt.submit(
                        PlayerSlot.ADMIN, Move.replace(UnitSet.of(handles), new Vec2d(east.x() + 0.5, east.z() + 0.5)));
                return;
            }
            long tick = core.queries().tick();
            if (state.ideal == null) {
                state.ideal = new double[CROWD];
                for (int k = 0; k < CROWD; k++) {
                    var orders = core.queries().orders(state.units[k]);
                    check(!orders.isEmpty(), "a unit got no order");
                    state.ideal[k] = idealTicks(
                            core,
                            unit(rt, state.units[k]),
                            orders.get(0).x(),
                            orders.get(0).z());
                }
                state.orderedAt = tick - 1;
                return;
            }
            for (int k = 0; k < CROWD; k++) {
                if (state.in[k]) {
                    continue;
                }
                MotionStore.Extra motion = core.queries().motion(state.units[k]);
                if (motion != null && motion.stuck() > state.mostStuck) {
                    state.mostStuck = motion.stuck();
                    UnitView stuck = unit(rt, state.units[k]);
                    state.worst = "unit " + k + " at " + stuck.x() + ", " + stuck.y() + ", " + stuck.z();
                }
                UnitView u = unit(rt, state.units[k]);
                if (u.state() == UnitState.IDLE && u.target() == null) {
                    state.in[k] = true;
                    state.arrived++;
                    long took = tick - state.orderedAt;
                    state.slowest = Math.max(state.slowest, took / state.ideal[k]);
                    check(
                            took <= 1.5 * state.ideal[k] + 10,
                            "unit " + k + " took " + took + " ticks, ideal " + state.ideal[k]);
                }
            }
            check(state.mostStuck < 100, "a unit went " + state.mostStuck + " ticks without progress: " + state.worst);
            if (state.arrived == CROWD) {
                PathService.Stats paths = core.queries().pathStats();
                LOG.info(
                        "two_hundred_cross: the slowest unit took {} times its ideal time; at most {} ticks without"
                                + " progress; {} requests, {} for the group; rungs {}",
                        String.format(Locale.ROOT, "%.2f", state.slowest),
                        state.mostStuck,
                        paths.requests(),
                        paths.groupRequests(),
                        Arrays.toString(core.queries().ladderRungs()));
                check(paths.groupRequests() == 1, "the group asked " + paths.groupRequests() + " times");
                h.succeed();
            }
        });
    }

    /**
     * The ticks a walker at the unit's speed needs along a cheapest path from where it stands to (tx, tz): each step's
     * length, slowed to half in shallow water and to six tenths on slow ground.
     */
    private static double idealTicks(MatchCore core, UnitView unit, double tx, double tz) {
        SurfaceGrid grid = core.queries().surfaces();
        double speed = core.queries().templates().byIndex(unit.templateIndex()).walkSpeed();
        int from = PathService.startNode(
                grid,
                unit.x(),
                unit.y(),
                unit.z(),
                core.queries().config().bounds().minY());
        GridAStar.Result path =
                new GridAStar().search(grid, from, (int) Math.floor(tx), (int) Math.floor(tz), 1_000_000);
        check(path.status() == PathStatus.OK, "no path from the west start: " + path.status());
        int[] nodes = path.nodes();
        double ticks = 0;
        for (int i = 1; i < nodes.length; i++) {
            int x = grid.nodeX(nodes[i]);
            int z = grid.nodeZ(nodes[i]);
            double length = x != grid.nodeX(nodes[i - 1]) && z != grid.nodeZ(nodes[i - 1]) ? Math.sqrt(2) : 1;
            int kind = Surface.kind(grid.surface(x, z, SurfaceGrid.nodeLayer(nodes[i])));
            double factor = kind == Surface.SHALLOW_WATER ? 0.5 : kind == Surface.SLOW ? 0.6 : 1;
            ticks += length / (speed * factor);
        }
        return ticks;
    }

    /**
     * PERF-05.2's correctness checks in {@code verifySlow} (M1 spec §1.6 D row 10): the crowd soak of 600 units without
     * budgets, its first {@code strategos.soak.ticks} ticks (all 12,000 by default): no failed order, at most 1 nudge
     * per 1,000 unit orders, no unit more than 400 ticks without progress.
     */
    @StrategosTest(batch = "map_soak", solo = true, timeoutTicks = 80_000)
    public static void crowdSoakOrders(GameTestHelper h) {
        int ticks = Integer.getInteger("strategos.soak.ticks", 12_000);
        CrowdSoak.start(
                h,
                new CrowdSoak.Config("crowd_soak_orders", 0, 300, ticks, false, 0, SOAK_LANE),
                r -> {
                    LOG.info(
                            "crowd soak: {} unit orders in {} groups, rungs {}, stuck histogram {}, most stuck {} ({})",
                            r.unitOrders(),
                            r.groupOrders(),
                            Arrays.toString(r.rungs()),
                            Arrays.toString(r.stuck()),
                            r.mostStuck(),
                            r.worst());
                    List<String> failures = r.failures();
                    if (failures.isEmpty()) {
                        h.succeed();
                    } else {
                        h.fail(String.join("; ", failures));
                    }
                },
                () -> {},
                () -> {});
    }

    /**
     * QA-11.5: {@code /strategos skirmish strategos:proving_ground observe seed=3} makes the match with seed 3; once it
     * has imported, the first-loop scenario runs bot against bot and the player is seated as its observer.
     */
    @StrategosTest(batch = "map_skirmish", solo = true, timeoutTicks = 40_000)
    public static void skirmishObserveSeatsTheObserver(GameTestHelper h) {
        BlockPos corner = h.absolutePos(BlockPos.ZERO);
        double x = corner.getX() + SKIRMISH_LANE * FAR;
        double z = corner.getZ() + 192;
        dev.strategos.gametest.net.MockViewer viewer = dev.strategos.gametest.net.MockViewer.join(
                h.getLevel(), "strategos-skirm", x, corner.getY() + 16, z, 2);
        var server = h.getLevel().getServer();
        server.getCommands()
                .performPrefixedCommand(
                        viewer.player()
                                .createCommandSourceStack()
                                .withPermission(4)
                                .withPosition(new net.minecraft.world.phys.Vec3(x, corner.getY() + 16, z)),
                        "strategos skirmish strategos:proving_ground observe seed=3");
        var registry = StrategosServer.get().matches();
        var state = new Object() {
            MatchRuntime match;
            int ai;
        };
        h.startSequence()
                .thenWaitUntil(() -> {
                    var seat = registry.seatOf(viewer.player().getUUID());
                    if (seat == null) {
                        viewer.poll();
                        StringBuilder said = new StringBuilder();
                        for (var p : viewer.capture()
                                .of(net.minecraft.network.protocol.game.ClientboundSystemChatPacket.class)) {
                            said.append(p.content().getString()).append("; ");
                        }
                        check(
                                false,
                                "not seated yet; the command said: " + said
                                        + registry.all().size() + " matches");
                    }
                    state.match = seat.match();
                    check(seat.slot().isAdmin(), "seated in slot " + seat.slot() + ", not as the observer");
                    check(state.match.core().queries().config().seed() == 3, "the seed is not 3");
                    state.match.addListener(out -> out.events().forEach(e -> {
                        if (e instanceof dev.strategos.core.sim.CommandResult r
                                && !r.issuer().isAdmin()) {
                            state.ai++;
                        }
                    }));
                })
                .thenWaitUntil(() -> check(state.ai > 0, "no computer player commanded yet"))
                .thenExecute(() -> {
                    check(core(state.match).queries().buildings().size() == 2, "the scenario's two centres");
                    var counters = StrategosServer.get().net().counters();
                    int before = counters.violations("strategos-skirm");
                    int[] units = core(state.match).queries().unitHandles();
                    viewer.send(dev.strategos.mod.server.net.payload.CommandsPayload.of(
                            new dev.strategos.core.protocol.CommandBatch(
                                    1,
                                    java.util.List.of(new dev.strategos.core.protocol.Move(
                                            dev.strategos.core.protocol.UnitSet.of(units[0]),
                                            new dev.strategos.core.shared.Vec2d(8.5, 8.5),
                                            dev.strategos.core.protocol.QueueMode.REPLACE,
                                            0)))));
                    check(
                            counters.violations("strategos-skirm") == before + 1 && !viewer.disconnected(),
                            "the observer's command was not dropped as one violation");
                    viewer.close();
                    registry.dispose(state.match.id());
                })
                .thenSucceed();
    }

    /** A proving-ground match in the test's lane east of it, {@code dz} blocks along Z, its band from the test's floor. */
    static MatchRuntime provingGround(GameTestHelper h, int lane, int dz) {
        MapManifest m = manifest(h);
        BlockPos corner = h.absolutePos(BlockPos.ZERO);
        BlockPos origin = new BlockPos(corner.getX() + lane * FAR, corner.getY(), corner.getZ() + dz);
        MatchSpace space = new MatchSpace(
                h.getLevel().dimension(), origin, m.sizeX(), m.sizeZ(), origin.getY(), origin.getY() + m.height() - 1);
        MatchOptions options = MatchFixture.options(SimHarness.DEFAULT_SEED, h)
                .withMap(PROVING_GROUND, origin.getY() + m.startSlots().get(0).y());
        return MatchFixture.create(h, space, options);
    }

    static MapManifest manifest(GameTestHelper h) {
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
