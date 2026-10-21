package dev.strategos.core.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.journal.JournalExport;
import dev.strategos.core.sim.journal.ReplayResult;
import dev.strategos.core.testkit.FakeBodies.Pose;
import java.nio.file.Path;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The harness's own behaviour: restore, runUntil, replay, fake bodies, injected faults, journal files and seed sets. */
class SimHarnessTest {

    private static SimHarness walking() {
        SimHarness h = SimHarness.builder().build();
        int[] units = h.spawn(new FactionSlot(0), new Vec2d(4.5, 8.5), 3, 2.0);
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(units), new Vec2d(24.5, 8.5)));
        h.runTicks(10);
        return h;
    }

    @Test
    void aRestoredHarnessContinuesWithTheSameHashesAndBodies() {
        SimHarness h = walking();
        SimHarness restored = SimHarness.restore(h.core().snapshot(), TemplateSet.builtin());
        assertThat(restored.bodies().count()).isEqualTo(3);
        int unit = h.queries().unitHandles()[0];
        Pose pose = restored.bodies().pose(unit);
        assertThat(pose).isNotNull();
        assertThat(pose.tick()).isEqualTo(h.tick());

        for (int i = 0; i < 100; i++) {
            h.step();
            restored.step();
            assertThat(restored.stateHash()).as("tick %d", h.tick()).isEqualTo(h.stateHash());
        }
        assertThat(restored.bodies().pose(unit)).isEqualTo(h.bodies().pose(unit));
    }

    @Test
    void bodiesFollowEveryFrameAndStayAtTheirLastPose() {
        SimHarness h = walking();
        int unit = h.queries().unitHandles()[0];
        UnitView view = h.queries().unit(unit);
        Pose pose = h.bodies().pose(unit);
        assertThat(view).isNotNull();
        assertThat(pose).isNotNull();
        assertThat(pose.x()).isEqualTo(view.x());
        assertThat(pose.z()).isEqualTo(view.z());
        assertThat(pose.yaw()).isEqualTo(view.yaw());
        assertThat(pose.tick()).isEqualTo(h.tick());
        assertThat(h.bodies().pose(unit + 1_000)).isNull();
    }

    @Test
    void runUntilStopsWhenTheConditionHoldsOrTheTicksRunOut() {
        SimHarness h = walking();
        int unit = h.queries().unitHandles()[0];

        assertThat(h.runUntil(() -> isIdle(h, unit), 5)).isFalse();
        assertThat(h.tick()).as("five steps after tick 11").isEqualTo(16);
        assertThat(h.runUntil(() -> isIdle(h, unit), 1_000)).isTrue();
        assertThat(h.tick()).isLessThan(1_016);
        long arrived = h.tick();
        assertThat(h.runUntil(() -> true, 10)).isTrue();
        assertThat(h.tick()).as("a condition already true takes no step").isEqualTo(arrived);
    }

    private static boolean isIdle(SimHarness h, int unit) {
        UnitView view = h.queries().unit(unit);
        return view != null && view.state() == UnitState.IDLE;
    }

    @Test
    void replayReachesTheLiveHash() {
        SimHarness h = walking();
        h.runTicks(100);

        ReplayResult replay = h.replay();

        assertThat(replay.matches()).isTrue();
        assertThat(replay.finalTick()).isEqualTo(h.tick());
        assertThat(replay.finalHash()).isEqualTo(h.stateHash());
    }

    @Test
    void throwingMetricsFailsTheStepOfItsTick() {
        SimHarness h = SimHarness.builder().metrics(new ThrowingMetrics(3)).build();
        h.runTicks(2);

        assertThatThrownBy(h::step).isInstanceOf(IllegalStateException.class).hasMessage("injected fault at tick 3");
    }

    @Test
    void aJournalFileRoundTripsAndReplays(@TempDir Path dir) {
        SimHarness h = walking();
        h.runTicks(50);
        Path file = dir.resolve("nested/walking.json");

        JournalFiles.write(file, new JournalExport(h.core().journal(), h.tick(), h.stateHash()));
        JournalExport read = JournalFiles.read(file);

        assertThat(read.liveTick()).isEqualTo(h.tick());
        assertThat(read.liveHash()).isEqualTo(h.stateHash());
        assertThat(read.journal().nonEmptyTicks()).isEqualTo(h.core().journal().nonEmptyTicks());
        assertThat(ReplayRunner.check(read.journal(), TemplateSet.builtin(), read.liveHash())
                        .ok())
                .isTrue();
    }

    @Test
    void aJournalFileThatDoesNotDecodeNamesTheFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("broken.json");
        java.nio.file.Files.writeString(file, "{\"v\":1}");

        assertThatThrownBy(() -> JournalFiles.read(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith(file.toString());
    }

    @Test
    void seedSetsAreReadInFileOrder() {
        assertThat(Seeds.first("canary")).isEqualTo(7);
        assertThat(Seeds.first("skeleton-20")).isZero();
        if (System.getProperty(Seeds.OVERRIDE) == null) {
            assertThat(Seeds.set("skeleton-20").toArray())
                    .containsExactly(LongStream.range(0, 20).toArray());
            assertThat(Seeds.set("m0-200")).hasSize(200).doesNotHaveDuplicates();
            assertThat(Seeds.set("isolation-50")).hasSize(50).doesNotHaveDuplicates();
        }
        assertThatThrownBy(() -> Seeds.first("no-such-set"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("/seeds/no-such-set.txt");
    }
}
