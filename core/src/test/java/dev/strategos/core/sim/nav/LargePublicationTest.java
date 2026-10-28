package dev.strategos.core.sim.nav;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.TerrainPage;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LargePublicationTest {
    private static final TemplateId HORSEMAN = new TemplateId("strategos:unit/horseman");

    /** Whether the harnesses hold a live horseman from the start; without one, the large grid is built on demand. */
    private boolean horseman;

    @ParameterizedTest(name = "live horseman: {0}")
    @ValueSource(booleans = {false, true})
    void incrementalInteriorBordersCornersAndCeilingsMatchFreshGeometry(boolean withHorseman) {
        horseman = withHorseman;
        VoxelWorld world = VoxelFixtures.flat(48, 48, 12, 1);
        SimHarness h = harness(world);
        for (int[] edit : new int[][] {
            {8, 1, 8},
            {15, 1, 8},
            {16, 1, 8},
            {8, 1, 15},
            {8, 1, 16},
            {15, 1, 15},
            {16, 1, 16},
            {31, 1, 31},
            {32, 1, 32},
            {15, 3, 16},
            {16, 3, 15},
            {31, 5, 31}
        }) {
            for (short cls : new short[] {VoxelFixtures.SOLID, VoxelFixtures.AIR}) {
                world.fill(edit[0], edit[1], edit[2], edit[0], edit[1], edit[2], cls);
                PageCoord page = PageCoord.of(edit[0], edit[2]);
                h.publish(TerrainPage.build(page, world.slice(page), new PageBuilder(MobilityClass.INFANTRY)));
                h.step();
                sameGeometry(h, harness(world));
            }
        }
    }

    @ParameterizedTest(name = "live horseman: {0}")
    @ValueSource(booleans = {false, true})
    void footprintBordersAndDuplicateCapturesMatchFreshGeometry(boolean withHorseman) {
        horseman = withHorseman;
        VoxelWorld world = VoxelFixtures.flat(48, 48, 12, 1);
        SimHarness h = harness(world);
        MatchState s = KernelAccess.state(h.core());
        for (int[] column : new int[][] {{15, 8}, {16, 8}, {15, 15}, {16, 16}}) {
            s.blockColumn(column[0], column[1]);
            h.runTicks(s.config().rules().pageK());
            SimHarness fresh = harnessWithFootprint(world, column[0], column[1]);
            sameGeometry(h, fresh);
            s.releaseColumn(column[0], column[1]);
            h.runTicks(s.config().rules().pageK());
            sameGeometry(h, harness(world));
        }
        s.blockColumn(8, 8);
        h.runTicks(s.config().rules().pageK());
        PageCoord page = new PageCoord(0, 0);
        h.publish(TerrainPage.build(page, world.slice(page), new PageBuilder(MobilityClass.INFANTRY)));
        world.fill(15, 1, 10, 15, 4, 10, VoxelFixtures.SOLID);
        h.publish(TerrainPage.build(page, world.slice(page), new PageBuilder(MobilityClass.INFANTRY)));
        h.step();
        sameGeometry(h, harnessWithFootprint(world, 8, 8));
    }

    @ParameterizedTest(name = "live horseman: {0}")
    @ValueSource(booleans = {false, true})
    void unpublishedNeighboursBecomeAvailableConservatively(boolean withHorseman) {
        horseman = withHorseman;
        VoxelWorld world = VoxelFixtures.flat(48, 48, 12, 1);
        SimHarness h = newHarness();
        for (int z = 0; z < 3; z++) {
            for (int x = 0; x < 3; x++) {
                PageCoord coord = new PageCoord(x, z);
                h.publish(TerrainPage.build(coord, world.slice(coord), new PageBuilder(MobilityClass.INFANTRY)));
                h.step();
            }
        }
        sameGeometry(h, harness(world));
    }

    @Test
    void aHorsemanArrivingLaterFindsTheGridCurrentAndKeepsItSo() {
        VoxelWorld world = VoxelFixtures.flat(48, 48, 12, 1);
        horseman = false;
        SimHarness h = harness(world);
        MatchState s = KernelAccess.state(h.core());
        s.blockColumn(15, 8);
        world.fill(31, 1, 31, 31, 3, 31, VoxelFixtures.SOLID);
        PageCoord page = PageCoord.of(31, 31);
        h.publish(TerrainPage.build(page, world.slice(page), new PageBuilder(MobilityClass.INFANTRY)));
        h.runTicks(s.config().rules().pageK());
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(HORSEMAN, new FactionSlot(0), new Vec2d(40.5, 40.5), 1, 0));
        h.step();
        horseman = true;
        SimHarness fresh = harnessWithFootprint(world, 15, 8);
        sameGeometry(h, fresh);
        s.releaseColumn(15, 8);
        s.blockColumn(16, 16);
        h.runTicks(s.config().rules().pageK());
        sameGeometry(h, harnessWithFootprint(world, 16, 16));
    }

    @Test
    void aCaptureBeforeTheRebuildBoundaryKeepsAPendingFootprintAwayFromAHorsemanSpawnedLater() {
        VoxelWorld world = VoxelFixtures.flat(48, 48, 12, 1);
        horseman = false;
        SimHarness lazy = harness(world);
        horseman = true;
        SimHarness live = harness(world);
        int k = KernelAccess.state(lazy.core()).config().rules().pageK();
        for (SimHarness h : new SimHarness[] {lazy, live}) {
            while (h.tick() % k != 1) h.step();
            KernelAccess.state(h.core()).blockColumn(15, 8);
            PageCoord other = new PageCoord(2, 2);
            h.publish(TerrainPage.build(other, world.slice(other), new PageBuilder(MobilityClass.INFANTRY)));
            h.step();
        }
        assertThat(KernelAccess.state(lazy.core()).surfaces().page(0, 0).hash())
                .as("the footprint is not rebuilt yet")
                .isEqualTo(KernelAccess.state(harness(world).core())
                        .surfaces()
                        .page(0, 0)
                        .hash());
        spawnHorseman(lazy);
        sameGeometry(lazy, live);
        lazy.runTicks(k);
        live.runTicks(k);
        sameGeometry(lazy, live);
        sameGeometry(lazy, harnessWithFootprint(world, 15, 8));
    }

    @Test
    void aHorsemanSpawnedInATickWithPageChangesFindsNoChangedLargePages() {
        VoxelWorld world = VoxelFixtures.flat(48, 48, 12, 1);
        horseman = false;
        SimHarness h = harness(world);
        world.fill(40, 1, 40, 40, 3, 40, VoxelFixtures.SOLID);
        PageCoord page = PageCoord.of(40, 40);
        h.publish(TerrainPage.build(page, world.slice(page), new PageBuilder(MobilityClass.INFANTRY)));
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(HORSEMAN, new FactionSlot(0), new Vec2d(10.5, 10.5), 1, 0));
        h.step();
        MatchState s = KernelAccess.state(h.core());
        assertThat(s.changedPageCount()).as("the infantry grid did change").isPositive();
        assertThat(s.changedPageCount(MobilityClass.LARGE)).isZero();
    }

    @Test
    void aHorsemanAfterTheLastOneDiedFindsTheGridAsIfItHadLived() {
        VoxelWorld world = VoxelFixtures.flat(48, 48, 12, 1);
        horseman = false;
        SimHarness lazy = harness(world);
        spawnHorseman(lazy);
        MatchState s = KernelAccess.state(lazy.core());
        s.surfaces(MobilityClass.LARGE);
        int[] handles = lazy.queries().unitHandlesOf(new FactionSlot(0));
        for (int handle : handles) s.removeUnit(handle);
        int k = s.config().rules().pageK();
        s.blockColumn(15, 8);
        lazy.runTicks(k);
        world.fill(40, 1, 40, 40, 3, 40, VoxelFixtures.SOLID);
        PageCoord page = PageCoord.of(40, 40);
        lazy.publish(TerrainPage.build(page, world.slice(page), new PageBuilder(MobilityClass.INFANTRY)));
        lazy.runTicks(k);
        spawnHorseman(lazy);
        horseman = true;
        SimHarness live = harness(world);
        KernelAccess.state(live.core()).blockColumn(15, 8);
        live.runTicks(k);
        live.runTicks(k);
        sameGeometry(lazy, live);
    }

    @Test
    void changesAcrossTicksAndAnEditWhileNoHorsemanLivesMatchAHorsemanThatLivedThrough() {
        VoxelWorld world = VoxelFixtures.flat(48, 48, 12, 1);
        horseman = false;
        SimHarness lazy = harness(world);
        horseman = true;
        SimHarness live = harness(world);
        int k = KernelAccess.state(lazy.core()).config().rules().pageK();
        for (SimHarness h : new SimHarness[] {lazy, live}) {
            MatchState s = KernelAccess.state(h.core());
            while (h.tick() % k != 1) h.step(); // the same place in the rebuild cycle
            s.blockColumn(15, 8);
            h.step();
            s.editVoxel(20, 1, 20, VoxelFixtures.SOLID);
            h.step();
            s.blockColumn(16, 16);
            s.releaseColumn(15, 8);
            h.runTicks(k);
            s.editVoxel(33, 2, 3, VoxelFixtures.SOLID);
            s.blockColumn(31, 31);
            h.step();
        }
        spawnHorseman(lazy);
        sameGeometry(lazy, live);
        lazy.runTicks(k);
        live.runTicks(k);
        sameGeometry(lazy, live);
    }

    @Test
    void aFootprintPlacedThisTickReachesTheGridInTheWorldPhaseWhetherOrNotAHorsemanLives() {
        VoxelWorld world = VoxelFixtures.flat(48, 48, 12, 1);
        horseman = false;
        SimHarness lazy = harness(world);
        horseman = true;
        SimHarness live = harness(world);
        KernelAccess.state(lazy.core()).blockColumn(15, 8);
        KernelAccess.state(live.core()).blockColumn(15, 8);
        sameGeometry(lazy, live); // neither sees it before the WORLD phase
        sameGeometry(lazy, harness(world));
        int k = KernelAccess.state(lazy.core()).config().rules().pageK();
        lazy.runTicks(k);
        live.runTicks(k);
        sameGeometry(lazy, live);
        sameGeometry(lazy, harnessWithFootprint(world, 15, 8));
    }

    private static void spawnHorseman(SimHarness h) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(HORSEMAN, new FactionSlot(0), new Vec2d(40.5, 44.5), 1, 0));
        h.step();
    }

    private SimHarness newHarness() {
        SimHarness h = SimHarness.builder().size(48, 48).yBand(0, 12, 1).build();
        if (horseman) {
            h.submit(PlayerSlot.ADMIN, new SpawnUnits(HORSEMAN, new FactionSlot(0), new Vec2d(40.5, 40.5), 1, 0));
            h.step();
        }
        return h;
    }

    private SimHarness harness(VoxelWorld world) {
        return harnessWithFootprint(world, -1, -1);
    }

    private SimHarness harnessWithFootprint(VoxelWorld world, int x, int z) {
        SimHarness h = newHarness();
        if (x >= 0) KernelAccess.state(h.core()).restoreColumn(x, z);
        h.publishAll(world);
        h.step();
        return h;
    }

    private static void sameGeometry(SimHarness actual, SimHarness expected) {
        SurfaceGrid a = KernelAccess.state(actual.core()).surfaces(MobilityClass.LARGE);
        SurfaceGrid b = KernelAccess.state(expected.core()).surfaces(MobilityClass.LARGE);
        for (int z = 0; z < 3; z++)
            for (int x = 0; x < 3; x++)
                assertThat(a.page(x, z).hash())
                        .as("page %s,%s", x, z)
                        .isEqualTo(b.page(x, z).hash());
        for (int x = 0; x < 48; x++)
            for (int z = 0; z < 48; z++)
                for (int layer = 0; layer < b.count(x, z); layer++)
                    for (int dir = 0; dir < Dir.COUNT; dir++)
                        assertThat(a.neighbour(x, z, layer, dir)).isEqualTo(b.neighbour(x, z, layer, dir));
    }
}
