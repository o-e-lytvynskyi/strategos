package dev.strategos.core.sim.nav;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.testkit.Seeds;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** PATH-11: footprint and headroom constraints, including neighbours on another page. */
class MobilityTest {
    @Test
    @Tag("slow")
    void bothGridsFitTheBudgetOnAPopulated512Map() {
        VoxelWorld world = VoxelFixtures.flat(512, 512, 16, 1);
        world.fill(0, 6, 252, 511, 6, 259, VoxelFixtures.SOLID);
        var h = dev.strategos.core.testkit.SimHarness.builder()
                .size(512, 512)
                .yBand(0, 16, 1)
                .build();
        h.publishAll(world);
        h.step();
        var state = dev.strategos.core.sim.KernelAccess.state(h.core());
        assertThat(state.surfaces().count(256, 256)).isEqualTo(2);
        assertThat(state.surfaces(MobilityClass.LARGE).count(256, 256)).isEqualTo(2);
        long bytes = h.queries().navigationBytes();
        assertThat(bytes).isGreaterThan(2L * 512 * 512);
        assertThat(bytes).isLessThanOrEqualTo(32L * 1024 * 1024);
        System.out.println("PATH-11 populated 512x512, two classes and a bridge: " + bytes + " navigation bytes");
    }

    @Test
    void aOneBlockGapIsOpenToInfantryAndClosedToLarge() {
        VoxelWorld world = VoxelFixtures.flat(48, 32, 8, 1);
        world.fill(23, 1, 0, 23, 4, 31, VoxelFixtures.SOLID);
        world.fill(23, 1, 16, 23, 4, 16, VoxelFixtures.AIR);
        assertThat(crosses(world.grid(MobilityClass.INFANTRY, NavOverlay.EMPTY)))
                .isTrue();
        assertThat(crosses(world.grid(MobilityClass.LARGE, NavOverlay.EMPTY))).isFalse();
        world.fill(23, 1, 15, 23, 4, 17, VoxelFixtures.AIR);
        assertThat(crosses(world.grid(MobilityClass.LARGE, NavOverlay.EMPTY)))
                .as("a three-block gap is a positive control for the large class")
                .isTrue();
    }

    @Test
    void aLargeUnitNeedsTwoAndAHalfBlocksOfHeadroom() {
        VoxelWorld world = VoxelFixtures.flat(32, 32, 8, 1);
        world.fill(4, 3, 4, 27, 3, 27, VoxelFixtures.SOLID);
        assertThat(hasFloor(world.grid(), 10, 10, 16)).isTrue();
        assertThat(hasFloor(world.grid(MobilityClass.LARGE, NavOverlay.EMPTY), 10, 10, 16))
                .isFalse();
        world.fill(4, 3, 4, 27, 3, 27, VoxelFixtures.AIR);
        world.fill(4, 4, 4, 27, 4, 27, VoxelFixtures.SOLID);
        assertThat(hasFloor(world.grid(MobilityClass.LARGE, NavOverlay.EMPTY), 10, 10, 16))
                .isTrue();
    }

    @Test
    void aFootprintSeesTheWallAcrossAPageBoundary() {
        VoxelWorld world = VoxelFixtures.flat(32, 32, 8, 1);
        world.fill(16, 1, 0, 16, 4, 31, VoxelFixtures.SOLID);
        assertThat(hasFloor(world.grid(), 15, 16, 16)).isTrue();
        assertThat(hasFloor(world.grid(MobilityClass.LARGE, NavOverlay.EMPTY), 15, 16, 16))
                .isFalse();
        world.fill(16, 1, 14, 16, 4, 18, VoxelFixtures.AIR);
        assertThat(hasFloor(world.grid(MobilityClass.LARGE, NavOverlay.EMPTY), 15, 16, 16))
                .isTrue();
    }

    @Test
    void aLargeBodyBoxSeesADiagonalBlockAcrossAPageCorner() {
        VoxelWorld world = VoxelFixtures.flat(32, 32, 8, 1);
        world.fill(16, 1, 16, 16, 4, 16, VoxelFixtures.SOLID);
        assertThat(hasFloor(world.grid(), 15, 15, 16)).isTrue();
        assertThat(hasFloor(world.grid(MobilityClass.LARGE, NavOverlay.EMPTY), 15, 15, 16))
                .as("a 1.4-wide Minecraft body box overlaps the diagonal block by 0.2 on each axis")
                .isFalse();
        world.fill(16, 1, 16, 16, 4, 16, VoxelFixtures.AIR);
        assertThat(hasFloor(world.grid(MobilityClass.LARGE, NavOverlay.EMPTY), 15, 15, 16))
                .isTrue();
    }

    @Test
    void reachabilityPerClassIsAFloodFill() {
        Seeds.set("skeleton-20").forEach(seed -> {
            Random random = new Random(seed);
            VoxelWorld world = VoxelFixtures.flat(40, 32, 8, 1);
            for (int k = 0; k < 100; k++) {
                int x = 2 + random.nextInt(36);
                int z = 2 + random.nextInt(28);
                world.fill(x, 1, z, x, 3 + random.nextInt(2), z, VoxelFixtures.SOLID);
            }
            for (MobilityClass mobility : MobilityClass.values()) {
                SurfaceGrid grid = world.grid(mobility, NavOverlay.EMPTY);
                List<Integer> nodes = new ArrayList<>();
                for (int x = 0; x < grid.sizeX(); x++) {
                    for (int z = 0; z < grid.sizeZ(); z++) {
                        for (int layer = 0; layer < grid.count(x, z); layer++) {
                            if (Surface.passable(grid.surface(x, z, layer))) nodes.add(grid.node(x, z, layer));
                        }
                    }
                }
                assertThat(nodes).isNotEmpty();
                for (int k = 0; k < 4; k++) {
                    int start = nodes.get(random.nextInt(nodes.size()));
                    Set<Integer> filled = ComponentsFloodFillTest.floodFill(grid, start);
                    BitSet reachable = grid.reachableFrom(start);
                    for (int node : nodes) {
                        assertThat(reachable.get(grid.component(node)))
                                .as("seed %s, %s, from %s to %s", seed, mobility, start, node)
                                .isEqualTo(filled.contains(node));
                    }
                }
            }
        });
    }

    private static boolean crosses(SurfaceGrid grid) {
        int from = PathService.startNode(grid, 10.5, 1, 16.5, 0);
        int to = PathService.startNode(grid, 36.5, 1, 16.5, 0);
        return from >= 0 && to >= 0 && grid.reachableFrom(from).get(grid.component(to));
    }

    private static boolean hasFloor(SurfaceGrid grid, int x, int z, int floor) {
        for (int layer = 0; layer < grid.count(x, z); layer++) {
            if (Surface.floor(grid.surface(x, z, layer)) == floor) return true;
        }
        return false;
    }
}
