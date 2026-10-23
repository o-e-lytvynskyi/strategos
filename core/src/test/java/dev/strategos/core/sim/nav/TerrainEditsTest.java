package dev.strategos.core.sim.nav;

import static org.assertj.core.api.Assertions.assertThat;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.sim.CorePorts;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.PageRef;
import dev.strategos.core.sim.TerrainPage;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The core's own terrain edits (M3 spec §1.6 row 5, T5): an edit rebuilds its page in the WORLD phase of the next
 * tick that is a multiple of {@code pageK}, as the world will be once the writer has written it; a later capture taken before the write reached the writer keeps
 * the edit applied, one taken after drops it, and one of unknown sample tick keeps it; edits survive a snapshot and
 * hash only once there are any.
 */
class TerrainEditsTest {
    private static final PageCoord PAGE = new PageCoord(0, 0);

    /** A 32 x 32 floor one block thick with, when {@code pillar}, a pillar three blocks high on it at (5, 5). */
    private static VoxelWorld world(boolean pillar) {
        VoxelWorld w = VoxelFixtures.flat(32, 32, 16, 1);
        if (pillar) {
            w.fill(5, 1, 5, 5, 3, 5, VoxelFixtures.SOLID);
        }
        return w;
    }

    private static SurfacePage reference(boolean pillar) {
        return world(pillar).grid().page(0, 0);
    }

    private static TerrainPage capture(boolean pillar, long sampleTick) {
        return TerrainPage.build(PAGE, world(pillar).slice(PAGE), new PageBuilder(MobilityClass.INFANTRY), sampleTick);
    }

    /** A match on the pillared world, the pillar then edited away by the core in one tick. */
    private static SimHarness edited() {
        SimHarness h = SimHarness.builder().build();
        h.publishAll(world(true));
        h.step();
        assertThat(h.queries().surfaces().page(0, 0)).isEqualTo(reference(true));
        MatchState s = KernelAccess.state(h.core());
        for (int y = 1; y <= 3; y++) {
            s.editVoxel(5, y, 5, BlockClass.AIR);
        }
        long rules = h.queries().config().rules().pageK();
        do {
            h.step(); // rebuilt at the next multiple of pageK
            if (h.tick() % rules != 0) {
                assertThat(h.queries().surfaces().page(0, 0)).isEqualTo(reference(true));
            }
        } while (h.tick() % rules != 0);
        return h;
    }

    @Test
    void anEditRebuildsItsPageInTheTickOfTheEdit() {
        SimHarness h = edited();

        assertThat(h.queries().surfaces().page(0, 0)).isEqualTo(reference(false));
        assertThat(h.queries().surfaces().page(1, 1))
                .isEqualTo(world(true).grid().page(1, 1));
        assertThat(KernelAccess.state(h.core()).terrainEdits().count(0)).isEqualTo(3);
    }

    @Test
    void aLaterCaptureKeepsTheEditsItDoesNotHoldAndForgetsTheOnesItDoes() {
        SimHarness h = edited();
        MatchState s = KernelAccess.state(h.core());
        long editTick = 1; // the edits were made after the first step, at the state's tick then

        h.publish(capture(true, editTick - 1)); // sampled before the edits' writes reached the writer: the pillar
        h.step();
        assertThat(h.queries().surfaces().page(0, 0))
                .as("the edits laid over it")
                .isEqualTo(reference(false));
        assertThat(s.terrainEdits().count(0)).isEqualTo(3);

        h.publish(capture(true, TerrainPage.UNKNOWN)); // a restart's prewarm: the writes may have been lost
        h.step();
        assertThat(h.queries().surfaces().page(0, 0)).isEqualTo(reference(false));
        assertThat(s.terrainEdits().count(0)).isEqualTo(3);

        h.publish(capture(false, editTick)); // sampled after them: the world holds them
        h.step();
        assertThat(s.terrainEdits().count(0)).isZero();
        assertThat(h.queries().surfaces().page(0, 0)).isEqualTo(reference(false));

        h.publish(capture(true, h.tick())); // a player builds the pillar again: the core's edits no longer apply
        h.step();
        assertThat(h.queries().surfaces().page(0, 0)).isEqualTo(reference(true));
    }

    @Test
    void editsSurviveASnapshotAndHashOnlyOnceThereAreAny() {
        SimHarness plain = SimHarness.builder().build();
        plain.publishAll(world(true));
        plain.step();
        plain.step();
        SimHarness h = edited();
        assertThat(h.stateHash()).isNotEqualTo(plain.stateHash());

        MatchSnapshot snapshot = h.core().snapshot();
        MatchSnapshot decoded = MatchSnapshotCodec.decode(
                        new Dynamic<>(JsonOps.INSTANCE, MatchSnapshotCodec.encode(JsonOps.INSTANCE, snapshot)),
                        Migrations.production())
                .getOrThrow();
        assertThat(decoded).isEqualTo(snapshot);
        assertThat(decoded.terrainEdits()).hasSize(12);
        Map<Long, byte[]> slices = new HashMap<>();
        VoxelWorld pillared = world(true);
        for (PageRef ref : snapshot.pages()) {
            VoxelSlice slice = pillared.slice(new PageCoord(ref.x(), ref.z()));
            slices.put(slice.hash(), slice.encode());
        }
        MatchCore restored = MatchCore.restore(decoded, TemplateSet.builtin(), CorePorts.of(() -> 0L), slices::get);
        assertThat(restored.queries().surfaces().page(0, 0)).isEqualTo(reference(false));
        assertThat(restored.stateHash()).isEqualTo(h.stateHash());
        assertThat(MatchSnapshotCodec.encode(JsonOps.INSTANCE, plain.core().snapshot())
                        .getAsJsonObject()
                        .has("terrainEdits"))
                .as("a match the core never edited saves no edits")
                .isFalse();
    }
}
