package dev.strategos.core.sim.nav;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.TerrainPage;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** PATH-11 through actual orders and movement, rather than grid reachability alone. */
class LargeMovementTest {
    private static final FactionSlot F0 = new FactionSlot(0);
    private static final TemplateId LARGE = new TemplateId("strategos:unit/large_test");
    private static final TemplateSet TEMPLATES = new TemplateSet(List.of(
            new UnitTemplate(TemplateSet.DEBUG_WALKER, 0, "strategos:unit_humanoid", 0.1875, 0.6f, 1.8f),
            new UnitTemplate(
                    LARGE,
                    1,
                    "Large test",
                    List.of("Cavalry"),
                    0.1875,
                    45,
                    "large",
                    "strategos:unit_mount",
                    "horse",
                    "",
                    1.4f,
                    2f,
                    0.7)));

    @Test
    void aLargeUnitWalksThroughAWideGapWithoutAStuckRung() {
        VoxelWorld world = VoxelFixtures.flat(48, 32, 8, 1);
        world.fill(23, 1, 0, 23, 4, 31, VoxelFixtures.SOLID);
        world.fill(23, 1, 15, 23, 4, 17, VoxelFixtures.AIR);
        SimHarness h = harness(world);
        int unit = large(h, new Vec2d(10.5, 16.5), 1, 0)[0];
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(unit), new Vec2d(36.5, 16.5)));
        boolean arrived = h.runUntil(() -> idleBeyond(h, unit, 35), 700);
        assertThat(arrived).isTrue();
        assertThat(h.queries().ladderRungs()).containsOnly(0);
    }

    @Test
    void aLargeUnitClimbsAOneBlockStep() {
        VoxelWorld world = VoxelFixtures.flat(48, 32, 8, 1);
        world.fill(24, 1, 0, 47, 1, 31, VoxelFixtures.SOLID);
        SimHarness h = harness(world);
        int unit = large(h, new Vec2d(10.5, 16.5), 1, 0)[0];
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(unit), new Vec2d(36.5, 16.5)));
        assertThat(h.runUntil(() -> idleBeyond(h, unit, 35), 700)).isTrue();
        assertThat(h.queries().unit(unit).y()).isEqualTo(2);
        assertThat(h.queries().ladderRungs()).containsOnly(0);
    }

    @Test
    void anUnreachableNarrowGapNeverLetsALargeUnitThrough() {
        VoxelWorld world = VoxelFixtures.flat(48, 32, 8, 1);
        world.fill(23, 1, 0, 23, 4, 31, VoxelFixtures.SOLID);
        world.fill(23, 1, 16, 23, 4, 16, VoxelFixtures.AIR);
        SimHarness h = harness(world);
        int unit = large(h, new Vec2d(10.5, 16.5), 1, 0)[0];
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(unit), new Vec2d(36.5, 16.5)));
        for (int k = 0; k < 700; k++) {
            h.step();
            assertThat(h.queries().unit(unit).x()).as("tick %s", h.tick()).isLessThan(22.3);
        }
    }

    @Test
    void largeMembersOfAMixedGroupUseTheWideOpening() {
        VoxelWorld world = VoxelFixtures.flat(96, 64, 8, 1);
        world.fill(48, 1, 0, 48, 4, 63, VoxelFixtures.SOLID);
        world.fill(48, 1, 16, 48, 4, 16, VoxelFixtures.AIR);
        world.fill(48, 1, 44, 48, 4, 48, VoxelFixtures.AIR);
        SimHarness h = harness(world);
        int[] foot = h.spawn(F0, new Vec2d(12.5, 14.5), 10, 3);
        int[] horse = large(h, new Vec2d(12.5, 32.5), 10, 3);
        int[] all = Arrays.copyOf(foot, foot.length + horse.length);
        System.arraycopy(horse, 0, all, foot.length, horse.length);
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(all), new Vec2d(80.5, 16.5)));
        boolean crossed = false;
        for (int k = 0; k < 1_200; k++) {
            h.step();
            for (int handle : horse) {
                UnitView unit = h.queries().unit(handle);
                if (unit.x() >= 47 && unit.x() < 50) {
                    assertThat(unit.z()).as("large unit at the wall").isBetween(44.0, 49.0);
                    crossed = true;
                }
            }
            if (Arrays.stream(all).allMatch(u -> idleBeyond(h, u, 70))) break;
        }
        assertThat(crossed).isTrue();
        assertThat(Arrays.stream(all).allMatch(u -> idleBeyond(h, u, 70))).isTrue();
        for (int rung = 2; rung <= 5; rung++)
            assertThat(h.queries().ladderRungs()[rung]).isZero();
    }

    @Test
    void publishingANeighbourRebuildsTheLargePageHaloAndKeepsOldSnapshotsImmutable() {
        VoxelWorld world = VoxelFixtures.flat(32, 32, 8, 1);
        world.fill(16, 1, 0, 16, 4, 31, VoxelFixtures.SOLID);
        SimHarness h = harness(world);
        MatchState state = KernelAccess.state(h.core());
        SurfaceGrid before = state.surfaces(MobilityClass.LARGE);
        assertThat(floorAt(before, 15, 17, 16)).isFalse();
        world.fill(16, 1, 14, 16, 4, 18, VoxelFixtures.AIR);
        PageCoord changed = new PageCoord(1, 1);
        h.publish(TerrainPage.build(changed, world.slice(changed), new PageBuilder(MobilityClass.INFANTRY)));
        h.step();
        assertThat(floorAt(state.surfaces(MobilityClass.LARGE), 15, 17, 16)).isTrue();
        assertThat(floorAt(before, 15, 17, 16)).isFalse();
        int[] pages = new int[state.changedPageCount(MobilityClass.LARGE)];
        for (int k = 0; k < pages.length; k++) pages[k] = state.changedPage(k, MobilityClass.LARGE);
        assertThat(pages).contains(2); // (0, 1), west of the page actually published
    }

    @Test
    void anEmptyPublishedLargeGridIsNotTheLegacyFlatWorldFallback() {
        SimHarness h = harness(new VoxelWorld(32, 8, 32));
        SurfaceGrid grid = KernelAccess.state(h.core()).surfaces(MobilityClass.LARGE);
        assertThat(grid.version()).isGreaterThan(0);
        assertThat(grid.count(10, 10)).isZero();
    }

    @Test
    void aDiagonalBorderEditRebuildsThePageWhoseBodyBoxOverlapsIt() {
        VoxelWorld world = VoxelFixtures.flat(32, 32, 8, 1);
        world.fill(16, 1, 16, 16, 4, 16, VoxelFixtures.SOLID);
        SimHarness h = harness(world);
        MatchState state = KernelAccess.state(h.core());
        assertThat(floorAt(state.surfaces(MobilityClass.LARGE), 15, 15, 16)).isFalse();
        world.fill(16, 1, 16, 16, 4, 16, VoxelFixtures.AIR);
        PageCoord changed = new PageCoord(1, 1);
        h.publish(TerrainPage.build(changed, world.slice(changed), new PageBuilder(MobilityClass.INFANTRY)));
        h.step();
        assertThat(floorAt(state.surfaces(MobilityClass.LARGE), 15, 15, 16)).isTrue();
        int[] pages = new int[state.changedPageCount(MobilityClass.LARGE)];
        for (int k = 0; k < pages.length; k++) pages[k] = state.changedPage(k, MobilityClass.LARGE);
        assertThat(pages).contains(0);
    }

    @Test
    void aMixedOrderMakesOneSharedPathPerMobilityClass() {
        SimHarness h = harness(VoxelFixtures.flat(96, 48, 8, 1));
        int[] foot = h.spawn(F0, new Vec2d(12.5, 14.5), 10, 3);
        int[] horse = large(h, new Vec2d(12.5, 32.5), 10, 3);
        int[] all = Arrays.copyOf(foot, foot.length + horse.length);
        System.arraycopy(horse, 0, all, foot.length, horse.length);
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(all), new Vec2d(80.5, 24.5)));
        h.runTicks(10);
        assertThat(h.queries().pathStats().groupRequests()).isEqualTo(2);
        assertThat(h.runUntil(() -> Arrays.stream(all).allMatch(u -> idleBeyond(h, u, 70)), 1_200))
                .isTrue();
        assertThat(h.queries().pathStats().noPath()).isZero();
        assertThat(h.queries().ladderRungs()[4]).isZero();
        assertThat(h.queries().ladderRungs()[5]).isZero();
    }

    @Test
    void aLargeUnitsRouteSurvivesSaveAndResumeBitForBit() {
        VoxelWorld world = VoxelFixtures.flat(96, 32, 8, 1);
        world.fill(40, 1, 0, 40, 4, 20, VoxelFixtures.SOLID);
        SimHarness live = harness(world);
        int unit = large(live, new Vec2d(12.5, 12.5), 1, 0)[0];
        live.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(unit), new Vec2d(82.5, 12.5)));
        live.runTicks(25);
        MatchSnapshot snapshot = live.core().snapshot();
        Map<Long, byte[]> slices = new TreeMap<>();
        for (var page : snapshot.pages())
            slices.put(
                    page.slice(), world.slice(new PageCoord(page.x(), page.z())).encode());
        SimHarness restored = SimHarness.restore(snapshot, TEMPLATES, slices::get);
        for (int k = 0; k < 400; k++) {
            live.step();
            restored.step();
            assertThat(restored.core().stateHash())
                    .as("tick %s", live.tick())
                    .isEqualTo(live.core().stateHash());
        }
    }

    private static SimHarness harness(VoxelWorld world) {
        SimHarness h = SimHarness.builder()
                .size(world.sizeX(), world.sizeZ())
                .yBand(0, world.height(), 1)
                .templates(TEMPLATES)
                .build();
        h.publishAll(world);
        h.step();
        return h;
    }

    private static int[] large(SimHarness h, Vec2d at, int count, double spread) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(LARGE, F0, at, count, spread));
        return h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .toArray();
    }

    private static boolean idleBeyond(SimHarness h, int handle, double x) {
        UnitView unit = h.queries().unit(handle);
        return unit != null && unit.state() == UnitState.IDLE && unit.target() == null && unit.x() > x;
    }

    private static boolean floorAt(SurfaceGrid grid, int x, int z, int floor) {
        for (int k = 0; k < grid.count(x, z); k++) if (Surface.floor(grid.surface(x, z, k)) == floor) return true;
        return false;
    }
}
