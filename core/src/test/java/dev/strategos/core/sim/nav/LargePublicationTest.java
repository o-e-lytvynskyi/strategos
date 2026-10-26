package dev.strategos.core.sim.nav;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.TerrainPage;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import org.junit.jupiter.api.Test;

class LargePublicationTest {
    @Test
    void incrementalInteriorBordersCornersAndCeilingsMatchFreshGeometry() {
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

    @Test
    void footprintBordersAndDuplicateCapturesMatchFreshGeometry() {
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

    @Test
    void unpublishedNeighboursBecomeAvailableConservatively() {
        VoxelWorld world = VoxelFixtures.flat(48, 48, 12, 1);
        SimHarness h = SimHarness.builder().size(48, 48).yBand(0, 12, 1).build();
        for (int z = 0; z < 3; z++) {
            for (int x = 0; x < 3; x++) {
                PageCoord coord = new PageCoord(x, z);
                h.publish(TerrainPage.build(coord, world.slice(coord), new PageBuilder(MobilityClass.INFANTRY)));
                h.step();
            }
        }
        sameGeometry(h, harness(world));
    }

    private static SimHarness harness(VoxelWorld world) {
        return harnessWithFootprint(world, -1, -1);
    }

    private static SimHarness harnessWithFootprint(VoxelWorld world, int x, int z) {
        SimHarness h = SimHarness.builder().size(48, 48).yBand(0, 12, 1).build();
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
