package dev.strategos.core.sim.persist;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonElement;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.StartSlot;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.journal.ReplayDriver;
import dev.strategos.core.sim.journal.ReplayResult;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * AI-18 (QA-12): the map's start slots, which every computer player knows, are the match config's: they survive a save
 * and its load, and the journal's replay, and a match with none encodes as it did before they existed.
 */
class StartSlotsPersistTest {
    private static final List<StartSlot> STARTS = List.of(new StartSlot(8, 1, 8, 0f), new StartSlot(40, 1, 40, 90f));

    private static SimHarness match(List<StartSlot> starts) {
        SimHarness h =
                SimHarness.builder().size(64, 64).yBand(0, 16, 1).starts(starts).build();
        h.publishAll(VoxelFixtures.flat(64, 64, 16, 1));
        h.runTicks(10);
        return h;
    }

    @Test
    void theStartsAreTheConfigsAndSurviveASaveAndItsLoad() {
        SimHarness h = match(STARTS);
        assertThat(h.queries().startPositions()).containsExactlyElementsOf(STARTS);

        MatchSnapshot snapshot = h.core().snapshot();
        JsonElement json = MatchSnapshotCodec.encode(JsonOps.INSTANCE, snapshot);
        MatchSnapshot decoded = MatchSnapshotCodec.decode(
                        new Dynamic<>(JsonOps.INSTANCE, json), Migrations.production())
                .getOrThrow();
        MatchCore restored =
                MatchCore.restore(decoded, TemplateSet.builtin(), dev.strategos.core.sim.CorePorts.of(() -> 0L));

        assertThat(restored.queries().startPositions()).containsExactlyElementsOf(STARTS);
        assertThat(restored.stateHash()).isEqualTo(h.stateHash());
    }

    @Test
    void theJournalsReplayKnowsThemToo() {
        SimHarness h = match(STARTS);
        h.runTicks(40);
        assertThat(h.core().journal().start().config().starts()).containsExactlyElementsOf(STARTS);
        ReplayResult replay = ReplayDriver.replay(h.core().journal(), TemplateSet.builtin());
        assertThat(replay.matches()).isTrue();
        assertThat(replay.finalHash()).isEqualTo(h.stateHash());
    }

    @Test
    void aMatchWithNoStartsEncodesWithoutThemAsBefore() {
        SimHarness h = match(List.of());
        JsonElement json = MatchSnapshotCodec.encode(JsonOps.INSTANCE, h.core().snapshot());
        assertThat(json.toString()).doesNotContain("\"starts\"");
    }
}
