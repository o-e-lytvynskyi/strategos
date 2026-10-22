package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;
import static dev.strategos.gametest.suites.Units.move;
import static dev.strategos.gametest.suites.Units.onlyUnit;
import static dev.strategos.gametest.suites.Units.spawn;
import static dev.strategos.gametest.suites.Units.unit;

import dev.strategos.core.port.JobScratch;
import dev.strategos.core.sim.PageRef;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.nav.PathJob;
import dev.strategos.core.sim.nav.PathRequest;
import dev.strategos.core.sim.nav.PathResult;
import dev.strategos.core.sim.nav.PathService;
import dev.strategos.core.sim.nav.PathStatus;
import dev.strategos.core.sim.nav.SurfaceGrid;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/** Paths in a real level (PATH-03.6, PATH-15.1): over steps and through a ford, and round a wall built mid-walk. */
public final class PathTests {
    private PathTests() {}

    /**
     * PATH-03.6: on a bank two blocks over the floor, a ridge of steps (a slab, a block, a slab) crosses the arena at x
     * 11..13, and a river two blocks deep at x 30..34 has one ford, a block shallower, at z 20..28. The match leaves out the
     * arena's first and last rows, where stone holds the river in, so the river has no end to walk round. A unit
     * ordered from the west to the east climbs the steps, wades through the ford and stops on its target; on every tick
     * it stands in the river's columns, it stands in the ford.
     */
    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 800)
    public static void unitCrossesStepsAndFord(GameTestHelper h) {
        buildStepsAndFord(h);
        double ground = Math.floor(h.getBounds().minY) + 3;
        MatchRuntime rt = MatchFixture.create(h, 0, 1, 48, 46, MatchOptions.gameTest(SimHarness.DEFAULT_SEED, ground));
        spawn(rt, 4.5, 39.5, 1, 0);
        var state = new Object() {
            boolean ordered;
            int riverTicks;
        };
        h.onEachTick(() -> {
            if (core(rt).queries().unitCount() != 1) {
                return;
            }
            int handle = onlyUnit(rt);
            if (!state.ordered) {
                state.ordered = true;
                move(rt, handle, 44.5, 39.5);
                return;
            }
            UnitView u = unit(rt, handle);
            if (u.x() >= 30 && u.x() < 35) {
                state.riverTicks++;
                check(u.z() >= 19 && u.z() < 28, "in the deep river at " + u.x() + ", " + u.z());
            }
            if (u.state() == UnitState.IDLE && u.target() == null && u.x() == 44.5) {
                PathService.Stats paths = core(rt).queries().pathStats();
                check(u.z() == 39.5, "stopped at z " + u.z());
                check(state.riverTicks > 0, "never crossed the river");
                check(paths.ok() >= 1 && paths.noPath() == 0, "paths: " + paths);
                h.succeed();
            }
        });
    }

    /**
     * PATH-15.1 with paths: a unit walks a straight path across the arena; once it is under way a wall three high goes
     * up across its route at x 30 (z 0..40, open at its north end), placed outside the writer. Its pages are rebuilt,
     * the unit asks again, and it walks round the wall's end: it never stands in the wall's columns, and it arrives.
     */
    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 800)
    public static void newWallBlocksPath(GameTestHelper h) {
        MatchRuntime rt = MatchFixture.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        spawn(rt, 4.5, 24.5, 1, 0);
        var state = new Object() {
            boolean ordered;
            boolean walled;
            long requestsBefore;
        };
        h.onEachTick(() -> {
            if (core(rt).queries().unitCount() != 1) {
                return;
            }
            int handle = onlyUnit(rt);
            if (!state.ordered) {
                state.ordered = true;
                move(rt, handle, 44.5, 24.5);
                return;
            }
            UnitView u = unit(rt, handle);
            if (!state.walled && u.x() > 8) {
                state.walled = true;
                state.requestsBefore = core(rt).queries().pathStats().requests();
                for (int z = 0; z <= 40; z++) {
                    for (int y = 2; y <= 4; y++) { // relative y 1 is the floor layer
                        h.setBlock(new BlockPos(30, y, z), Blocks.STONE);
                    }
                }
                return;
            }
            if (state.walled) {
                check(!(u.x() >= 30 && u.x() < 31 && u.z() < 41), "inside the wall at " + u.x() + ", " + u.z());
            }
            if (u.state() == UnitState.IDLE && u.target() == null && u.x() == 44.5) {
                check(state.walled, "arrived before the wall went up");
                check(u.z() == 24.5, "stopped at z " + u.z());
                long asked = core(rt).queries().pathStats().requests() - state.requestsBefore;
                check(asked >= 1, "no new request after the wall");
                h.succeed();
            }
        });
    }

    /**
     * PATH-08.3: on a 96 x 96 floor laid by the test, a unit walks a path in two levels (its goal six pages away); once
     * it is under way a wall three high goes up across its route at x 48 (z 0..79), placed outside the writer. On the
     * tick the wall's pages are published, a fresh request already goes round the wall's end in under 1,500
     * expansions, so the portals followed the wall within one rebuild cycle (stale portals send the search to its flat
     * fallback, about 2,500 here); the unit asks again, walks round it, never stands in it, and arrives.
     */
    @StrategosTest(arena = "arena_air_96", timeoutTicks = 1_600)
    public static void hierarchicalPathsFollowANewWall(GameTestHelper h) {
        for (int x = 0; x < 96; x++) {
            for (int z = 0; z < 96; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.SMOOTH_STONE); // relative y 1 is the floor layer
            }
        }
        MatchRuntime rt = MatchFixture.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        spawn(rt, 4.5, 48.5, 1, 0);
        var state = new Object() {
            boolean ordered;
            long[] before;
            boolean walled;
            boolean checked;
        };
        h.onEachTick(() -> {
            if (core(rt).queries().unitCount() != 1) {
                return;
            }
            int handle = onlyUnit(rt);
            if (!state.ordered) {
                state.ordered = true;
                move(rt, handle, 91.5, 48.5);
                return;
            }
            UnitView u = unit(rt, handle);
            if (!state.walled && u.x() > 8) {
                state.walled = true;
                state.before = wallPageSlices(rt);
                for (int z = 0; z < 80; z++) {
                    for (int y = 2; y <= 4; y++) {
                        h.setBlock(new BlockPos(48, y, z), Blocks.STONE);
                    }
                }
                return;
            }
            if (state.walled) {
                check(!(u.x() >= 48 && u.x() < 49 && u.z() < 80), "inside the wall at " + u.x() + ", " + u.z());
                if (!state.checked && published(state.before, wallPageSlices(rt))) {
                    state.checked = true;
                    SurfaceGrid grid = core(rt).queries().surfaces();
                    int from = PathService.startNode(
                            grid, 4.5, u.y(), 48.5, rt.space().minY());
                    PathResult fresh = new PathJob(
                                    grid, new PathRequest(1, handle, 0, from, 91.5, 48.5, 20_000, 0, false))
                            .run(new JobScratch());
                    check(fresh.status() == PathStatus.OK, "a fresh request: " + fresh.status());
                    boolean round = false;
                    for (int i = 0; i < fresh.waypoints().count(); i++) {
                        round |= fresh.waypoints().z(i) >= 80;
                    }
                    check(round, "a fresh request still goes through the wall: " + fresh.waypoints());
                    check(
                            fresh.expansions() < 1_500,
                            "the portals did not follow the wall, a flat search did: " + fresh.expansions()
                                    + " expansions");
                }
            }
            if (u.state() == UnitState.IDLE && u.target() == null && u.x() == 91.5) {
                check(state.checked, "arrived before the wall's pages were published");
                check(u.z() == 48.5, "stopped at z " + u.z());
                h.succeed();
            }
        });
    }

    /** The slice hashes of the pages the wall stands in: x 48 is page column 3, z 0..79 page rows 0..4. */
    private static long[] wallPageSlices(MatchRuntime rt) {
        long[] slices = new long[5];
        for (PageRef ref : core(rt).queries().pageRefs()) {
            if (ref.x() == 3 && ref.z() < 5) {
                slices[ref.z()] = ref.slice();
            }
        }
        return slices;
    }

    private static boolean published(long[] before, long[] now) {
        for (int i = 0; i < before.length; i++) {
            if (before[i] == now[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * The bank, the ridge of steps, the river and its ford, and the bank rows holding the river at the arena's first and
     * last z. GameTestHelper positions count from the structure block, a block under the floor: relative y 1 is the
     * floor layer, 2..4 the arena's air.
     */
    private static void buildStepsAndFord(GameTestHelper h) {
        for (int x = 0; x < 48; x++) {
            boolean river = x >= 30 && x <= 34;
            for (int z = 0; z < 48; z++) {
                boolean held = river && (z == 0 || z == 47);
                boolean ford = river && z >= 20 && z <= 28;
                if (!river || held) {
                    h.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
                    h.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
                } else if (ford) {
                    h.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
                    h.setBlock(new BlockPos(x, 3, z), Blocks.WATER);
                } else {
                    h.setBlock(new BlockPos(x, 2, z), Blocks.WATER);
                    h.setBlock(new BlockPos(x, 3, z), Blocks.WATER);
                }
                if (x == 11 || x == 13) {
                    h.setBlock(new BlockPos(x, 4, z), Blocks.SMOOTH_STONE_SLAB);
                } else if (x == 12) {
                    h.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
                }
            }
        }
    }
}
