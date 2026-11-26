package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.journal.JournalExport;
import dev.strategos.core.sim.journal.JournalFormat;
import dev.strategos.core.sim.journal.JournalWriter;
import dev.strategos.core.sim.journal.ReplayDriver;
import dev.strategos.core.sim.nav.MobilityClass;
import dev.strategos.core.sim.nav.PageBuilder;
import dev.strategos.core.sim.nav.PageCoord;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PendingTerrainReplayTest {
    private record Scene(SimHarness original, VoxelWorld world) {}

    private static Scene scene() {
        var h = SimHarness.builder().size(32, 32).yBand(0, 31, 1).build();
        var world = VoxelFixtures.flat(32, 32, 32, 1);
        h.publishAll(world);
        var handles = h.spawn(new FactionSlot(0), new Vec2d(4, 4), 1, 1);
        h.submit(new PlayerSlot(0), Move.replace(UnitSet.of(handles), new Vec2d(26, 26)));
        h.runTicks(10);
        return new Scene(h, world);
    }

    private static List<TerrainPage> capture(VoxelWorld world) {
        var pages = new ArrayList<TerrainPage>();
        for (int z = 0; z < 2; z++)
            for (int x = 0; x < 2; x++) {
                var coord = new PageCoord(x, z);
                pages.add(TerrainPage.build(coord, world.slice(coord), new PageBuilder(MobilityClass.INFANTRY)));
            }
        return pages;
    }

    @Test
    void missingGeometryKeepsItsBaselineAcrossPausedResaveAndAnExactBinaryJournalReplay() {
        var scene = scene();
        var original = scene.original();
        var saved = original.core().snapshot();
        var back = SimHarness.restore(saved, original.queries().templates());
        assertThat(back.core().snapshot().pages()).hasSize(4).allMatch(PageRef::pending);
        var available = original.core().journal().segments().getFirst();
        assertThat(available.slice(saved.pages().getFirst().slice())).isNotNull();
        back = SimHarness.restore(back.core().snapshot(), original.queries().templates(), available::slice);
        assertThat(back.core().snapshot().pages()).hasSize(4).allMatch(PageRef::pending);
        for (var page : capture(scene.world())) back.schedule(back.tick() + 1, page);
        back.runTicks(5);
        assertThat(back.core().snapshot().paths().lastPageChange())
                .isEqualTo(saved.paths().lastPageChange());
        assertThat(back.core().snapshot().pages()).hasSize(4).noneMatch(PageRef::pending);
        exactReplay(back);
    }

    @Test
    void aGenuinelyChangedFirstCaptureStillInvalidatesRoutesAtItsActualTick() {
        var scene = scene();
        var original = scene.original();
        var back = SimHarness.restore(
                original.core().snapshot(), original.queries().templates());
        scene.world().fill(8, 1, 8, 9, 3, 9, VoxelFixtures.SOLID);
        long changed = back.tick() + 1;
        for (var page : capture(scene.world())) back.schedule(changed, page);
        back.runTicks(5);
        assertThat(back.core().snapshot().paths().lastPageChange()).isEqualTo(changed);
        exactReplay(back);
    }

    @Test
    void aPendingBaselineNeedsNoGeometryButAPublishedPageStillRequiresItsRealSlice() {
        var scene = scene();
        var saved = scene.original().core().snapshot();
        assertThatThrownBy(() -> new JournalWriter(saved, SliceSource.NONE, 20, 2400))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no slice");
        var pending = SimHarness.restore(saved, scene.original().queries().templates())
                .core()
                .snapshot();
        var writer = new JournalWriter(pending, SliceSource.NONE, 20, 2400);
        assertThat(writer.view().start().pages()).hasSize(4).allMatch(PageRef::pending);
    }

    @Test
    void pendingReferencesSurviveAWholeJournalSegmentBeforeTheirFirstCapture() {
        var scene = scene();
        var back = SimHarness.restore(
                scene.original().core().snapshot(), scene.original().queries().templates());
        back.runTicks(1300);
        for (var page : capture(scene.world())) back.schedule(back.tick() + 1, page);
        back.runTicks(5);
        assertThat(back.core().journal().segments()).hasSizeGreaterThan(1);
        exactReplay(back);
    }

    private static void exactReplay(SimHarness h) {
        var decoded = JournalFormat.decode(
                JournalFormat.encode(new JournalExport(h.core().journal(), h.tick(), h.stateHash())));
        var result = ReplayDriver.replay(decoded.journal(), h.queries().templates());
        assertThat(result.matches()).isTrue();
        assertThat(result.finalTick()).isEqualTo(h.tick());
        assertThat(result.finalHash()).isEqualTo(h.stateHash());
    }
}
