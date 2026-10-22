package dev.strategos.mod.server.debug;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.DebugFeedMsg;
import dev.strategos.core.protocol.DebugLayer;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.nav.Surface;
import dev.strategos.core.sim.nav.SurfaceGrid;
import dev.strategos.core.sim.nav.SurfacePage;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PATH-10's layers as the feed builds them, on harness matches with no level: surfaces with their floors, kinds and
 * layers; components that tell the two sides of a wall apart; paths and markers of the units in the area only, a path
 * while its request is in flight too; the panel's counts and timers; parts within the bounds.
 */
class DebugFramesTest {
    private static final FactionSlot F0 = new FactionSlot(0);

    /** The match's local (0, 0) at the band's bottom, which the harness puts at Y 0. */
    private static final DebugFeedMsg.Origin ORIGIN = new DebugFeedMsg.Origin(100, 0, -200);

    @Test
    void cellsCarryFloorsKindsLayersAndComponents() {
        VoxelWorld w = VoxelFixtures.flat(40, 24, 16, 1);
        w.set(3, 1, 3, VoxelFixtures.WATER);
        w.fill(10, 1, 0, 10, 4, 23, VoxelFixtures.SOLID); // a wall four high across the match
        SurfaceGrid grid = w.grid();

        DebugFeedMsg.Cells surfaces = DebugFrames.cells(grid, ORIGIN, 0, 0, DebugLayer.SURFACES);
        assertThat(surfaces.cells()).hasSize(256);
        assertThat(cell(surfaces, 0, 0).floor())
                .as("on the ground, one block up")
                .isEqualTo(16);
        assertThat(cell(surfaces, 0, 0).value())
                .as("ground, the column's top layer")
                .isEqualTo(Surface.GROUND);
        assertThat(cell(surfaces, 3, 3).value()).isEqualTo(Surface.SHALLOW_WATER);
        assertThat(cell(surfaces, 10, 5).floor()).as("the wall's top").isEqualTo(5 * 16);

        DebugFeedMsg.Cells components = DebugFrames.cells(grid, ORIGIN, 0, 0, DebugLayer.COMPONENTS);
        assertThat(cell(components, 0, 0).value())
                .isEqualTo(cell(components, 9, 15).value());
        assertThat(cell(components, 0, 0).value())
                .as("the wall's two sides")
                .isNotEqualTo(cell(components, 11, 0).value());

        DebugFeedMsg.Cells corner = DebugFrames.cells(grid, ORIGIN, 2, 1, DebugLayer.SURFACES);
        assertThat(corner.cells())
                .as("only the page's columns inside the 40 x 24 match: x 32..39, z 16..23")
                .hasSize(64);
        assertThat(corner.origin()).isEqualTo(ORIGIN);
    }

    @Test
    void pathsAndMarksCoverTheUnitsInTheArea() {
        SimHarness h = harness(VoxelFixtures.flat(64, 64, 16, 1));
        int walker = h.spawn(F0, new Vec2d(10.5, 10.5), 1, 0)[0];
        int far = h.spawn(F0, new Vec2d(55.5, 55.5), 1, 0)[0];
        DebugFrames.Area area = DebugFrames.Area.around(10.5, 10.5, 32, 64, 64);
        assertThat(area).isEqualTo(new DebugFrames.Area(0, 0, 42, 42));

        assertThat(DebugFrames.paths(h.queries(), ORIGIN, area))
                .as("idle units: one empty part")
                .singleElement()
                .satisfies(p -> assertThat(p.lines()).isEmpty());
        // one order each: an order to both would give each its own place round the target (ORD-03)
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(walker), new Vec2d(40.5, 10.5)));
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(far), new Vec2d(40.5, 10.5)));
        h.step(); // 30 blocks: a request in flight, steering straight at the target meanwhile

        DebugFeedMsg.PathLine waiting = onlyLine(h, area);
        assertThat(waiting.handle()).isEqualTo(walker);
        assertThat(waiting.waiting()).isTrue();
        assertThat(waiting.points()).hasSize(2);
        assertThat(waiting.points().get(1)).isEqualTo(new DebugFeedMsg.Point(40 * 16 + 8, 16, 10 * 16 + 8));

        h.step();
        h.step();
        DebugFeedMsg.PathLine found = onlyLine(h, area);
        assertThat(found.waiting()).isFalse();
        assertThat(found.status()).as("reaches the goal").isZero();
        assertThat(found.points().get(found.points().size() - 1))
                .isEqualTo(new DebugFeedMsg.Point(40 * 16 + 8, 16, 10 * 16 + 8));

        List<DebugFeedMsg.Marks> marks = DebugFrames.marks(h.queries(), ORIGIN, area);
        assertThat(marks)
                .singleElement()
                .satisfies(m -> assertThat(m.marks()).singleElement().satisfies(mark -> {
                    assertThat(mark.handle()).isEqualTo(walker);
                    assertThat(mark.tier()).as("moving").isOne();
                    assertThat(mark.rung()).as("no ladder before T23").isZero();
                    assertThat(mark.at().y()).as("on the ground").isEqualTo(16);
                }));
    }

    @Test
    void manyPathsSplitIntoParts() {
        SimHarness h = harness(VoxelFixtures.flat(64, 64, 16, 1));
        int[] units = h.spawn(F0, new Vec2d(20.5, 20.5), DebugFeedMsg.MAX_LINES + 1, 8);
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(units), new Vec2d(60.5, 60.5)));
        h.step();

        List<DebugFeedMsg.Paths> parts = DebugFrames.paths(h.queries(), ORIGIN, new DebugFrames.Area(0, 0, 64, 64));
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0).lines()).hasSize(DebugFeedMsg.MAX_LINES);
        assertThat(parts.get(1).lines()).hasSize(1);
    }

    @Test
    void thePanelCarriesTheCountsAndTheTimers() {
        SimHarness h = harness(VoxelFixtures.flat(32, 32, 16, 1));
        h.spawn(F0, new Vec2d(10.5, 10.5), 2, 2);

        DebugFeedMsg.Panel panel = DebugFrames.panel(7, h.queries(), h.core().timers());

        assertThat(panel.matchId()).isEqualTo(7);
        assertThat(panel.matchTick()).isEqualTo(h.tick());
        assertThat(panel.units()).isEqualTo(2);
        assertThat(panel.queue()).isZero();
        assertThat(panel.timings())
                .extracting(DebugFeedMsg.Timing::name)
                .startsWith("INGEST")
                .endsWith("step", "adapter", "total");
    }

    @Test
    void anAreaIsCutToTheMatch() {
        assertThat(DebugFrames.Area.around(-50, 5, 8, 32, 32).isEmpty()).isTrue();
        DebugFrames.Area edge = DebugFrames.Area.around(30.5, 1.5, 8, 32, 32);
        assertThat(edge).isEqualTo(new DebugFrames.Area(22, 0, 32, 9));
        assertThat(edge.contains(31.9, 8.9)).isTrue();
        assertThat(edge.contains(21.9, 5)).isFalse();
    }

    private static DebugFeedMsg.PathLine onlyLine(SimHarness h, DebugFrames.Area area) {
        List<DebugFeedMsg.Paths> parts = DebugFrames.paths(h.queries(), ORIGIN, area);
        assertThat(parts).hasSize(1);
        assertThat(parts.get(0).lines()).as("the walker's, not the far unit's").hasSize(1);
        return parts.get(0).lines().get(0);
    }

    /** The topmost surface of column (x, z) of the page: a column's surfaces come topmost first. */
    private static DebugFeedMsg.Cell cell(DebugFeedMsg.Cells cells, int x, int z) {
        int column = SurfacePage.column(x, z);
        return cells.cells().stream()
                .filter(c -> c.column() == column)
                .findFirst()
                .orElseThrow();
    }

    private static SimHarness harness(VoxelWorld world) {
        SimHarness h = SimHarness.builder().size(world.sizeX(), world.sizeZ()).build();
        h.publishAll(world);
        return h;
    }
}
