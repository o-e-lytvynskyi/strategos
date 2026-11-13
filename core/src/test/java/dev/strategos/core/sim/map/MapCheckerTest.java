package dev.strategos.core.sim.map;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.MapWaypoint;
import dev.strategos.core.content.StartSlot;
import dev.strategos.core.content.TerrainJob;
import dev.strategos.core.sim.nav.MobilityClass;
import dev.strategos.core.sim.nav.NavOverlay;
import dev.strategos.core.sim.nav.SurfaceGrid;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The map checker bites (MAP-02.1–3): on a 64 x 32 floor with a wall across it, it finds unequal resources, a start
 * the other cannot reach, a band crossed at the wrong number of places, unequal walks and a low hill.
 */
final class MapCheckerTest {
    private static final int FLOOR = 4;
    private static final List<StartSlot> STARTS =
            List.of(new StartSlot(8, FLOOR + 1, 16, 0f), new StartSlot(55, FLOOR + 1, 16, 0f));

    /** A floor at Y 0–4 and a wall at X 30–32 up to Y 8, open at the rows {@code gaps} names (inclusive pairs). */
    private static VoxelWorld world(int... gaps) {
        VoxelWorld w = new VoxelWorld(64, 16, 32);
        w.fill(0, 0, 0, 63, FLOOR, 31, VoxelFixtures.SOLID);
        for (int z = 0; z < 32; z++) {
            boolean open = false;
            for (int k = 0; k + 1 < gaps.length; k += 2) {
                open |= z >= gaps[k] && z <= gaps[k + 1];
            }
            if (!open) {
                w.fill(30, FLOOR + 1, z, 32, 8, z, VoxelFixtures.SOLID);
            }
        }
        return w;
    }

    private static MapChecker.Input input(
            VoxelWorld w, List<MapChecker.Site> sites, List<Map<String, MapWaypoint>> drops, List<TerrainJob> jobs) {
        Map<MobilityClass, SurfaceGrid> grids = new EnumMap<>(MobilityClass.class);
        for (MobilityClass m : MobilityClass.values()) {
            grids.put(m, w.grid(m, NavOverlay.EMPTY));
        }
        MapChecker.Columns columns = new MapChecker.Columns() {
            @Override
            public int groundTop(int x, int z) {
                int y = w.height() - 1;
                while (y > 0 && w.get(x, y, z) == VoxelFixtures.AIR) {
                    y--;
                }
                return y;
            }

            @Override
            public boolean clear(int x, int z) {
                return groundTop(x, z) == FLOOR;
            }
        };
        return new MapChecker.Input(STARTS, grids, columns, sites, drops, jobs);
    }

    /** Wood behind each start, out of the other's 48 blocks. */
    private static List<MapChecker.Site> wood(double west, double east) {
        return List.of(new MapChecker.Site("wood", 1, 16, west), new MapChecker.Site("wood", 62, 16, east));
    }

    @Test
    void resourcesWithinFivePerCentPassAndBeyondFail() {
        VoxelWorld w = world(12, 17);
        assertThat(MapChecker.check(input(w, wood(100, 96), List.of(), List.of()))
                        .problems())
                .isEmpty();
        assertThat(MapChecker.check(input(w, wood(100, 90), List.of(), List.of()))
                        .problems())
                .singleElement()
                .asString()
                .contains("resource wood");
    }

    @Test
    void aWallWithoutAGapLeavesTheEastStartUnreached() {
        assertThat(MapChecker.check(input(world(), List.of(), List.of(), List.of()))
                        .problems())
                .anyMatch(p -> p.contains("start 1 is not reached"));
    }

    @Test
    void aBandCountsItsCrossingsAndTheirWidths() {
        VoxelWorld w = world(4, 9, 20, 21);
        TerrainJob two = TerrainJob.crossings(TerrainJob.FOREST_LINE, 30, 0, 32, 31, 2, 2);
        TerrainJob wide = TerrainJob.crossings(TerrainJob.FOREST_LINE, 30, 0, 32, 31, 2, 4);
        TerrainJob three = TerrainJob.crossings(TerrainJob.FOREST_LINE, 30, 0, 32, 31, 3, 2);
        assertThat(MapChecker.check(input(w, List.of(), List.of(), List.of(two)))
                        .problems())
                .isEmpty();
        assertThat(MapChecker.check(input(w, List.of(), List.of(), List.of(wide)))
                        .problems())
                .singleElement()
                .asString()
                .contains("2 wide at 20");
        assertThat(MapChecker.check(input(w, List.of(), List.of(), List.of(three)))
                        .problems())
                .singleElement()
                .asString()
                .contains("at 2 places, not 3");
    }

    @Test
    void unequalWalksToTheDropsitesFail() {
        VoxelWorld w = world(12, 17);
        var near = List.of(
                Map.of("wood", new MapWaypoint(12, FLOOR + 1, 16)), Map.of("wood", new MapWaypoint(51, FLOOR + 1, 16)));
        var far = List.of(
                Map.of("wood", new MapWaypoint(12, FLOOR + 1, 16)), Map.of("wood", new MapWaypoint(45, FLOOR + 1, 16)));
        assertThat(MapChecker.check(input(w, List.of(), near, List.of())).problems())
                .isEmpty();
        assertThat(MapChecker.check(input(w, List.of(), far, List.of())).problems())
                .singleElement()
                .asString()
                .contains("walk to the wood site");
    }

    @Test
    void aHillBelowItsRiseFails() {
        VoxelWorld w = world(12, 17);
        for (int step = 0; step < 3; step++) { // a stepped pyramid, its top 3 above the floor at (21, 21)
            w.fill(17 + step, FLOOR + 1 + step, 17 + step, 25 - step, FLOOR + 1 + step, 25 - step, VoxelFixtures.SOLID);
        }
        TerrainJob low = TerrainJob.hill(21, 21, 10, 10, 6);
        TerrainJob enough = TerrainJob.hill(21, 21, 10, 10, 3);
        assertThat(MapChecker.check(input(w, List.of(), List.of(), List.of(enough)))
                        .problems())
                .isEmpty();
        assertThat(MapChecker.check(input(w, List.of(), List.of(), List.of(low)))
                        .problems())
                .anyMatch(p -> p.contains("rises 3 blocks, not 6"));
    }
}
