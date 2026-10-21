package dev.strategos.core.sim.journal;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

class JournalCodecTest {

    @Test
    void anExportRoundTripsThroughJsonAndReplaysToTheLiveHash() {
        SimHarness h = RandomRun.run(5, 300);
        JournalExport export = new JournalExport(h.core().journal(), h.tick(), h.stateHash());

        JsonElement json =
                JournalCodec.CODEC.encodeStart(JsonOps.INSTANCE, export).getOrThrow();
        assertThat(json.getAsJsonObject().get("v").getAsInt()).isEqualTo(1);
        assertThat(json.getAsJsonObject().get("liveHash").getAsString()).hasSize(16);
        JournalExport decoded = JournalCodec.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        Journal original = export.journal();
        Journal copy = decoded.journal();
        assertThat(copy.start()).isEqualTo(original.start());
        assertThat(copy.lastTick()).isEqualTo(original.lastTick());
        assertThat(copy.nonEmptyTicks()).isEqualTo(original.nonEmptyTicks());
        assertThat(copy.checkpointEvery()).isEqualTo(original.checkpointEvery());
        assertThat(copy.checkpointTicks()).containsExactly(original.checkpointTicks());
        assertThat(copy.checkpointHashes()).containsExactly(original.checkpointHashes());
        assertThat(decoded.liveTick()).isEqualTo(export.liveTick());
        assertThat(decoded.liveHash()).isEqualTo(export.liveHash());

        ReplayResult replay = ReplayDriver.replay(copy, TemplateSet.builtin());
        assertThat(replay.matches()).isTrue();
        assertThat(replay.finalHash()).isEqualTo(decoded.liveHash());
    }

    @Test
    void anotherVersionFails() {
        SimHarness h = RandomRun.run(5, 30);
        JsonElement json = JournalCodec.CODEC
                .encodeStart(JsonOps.INSTANCE, new JournalExport(h.core().journal(), h.tick(), h.stateHash()))
                .getOrThrow();
        json.getAsJsonObject().add("v", new JsonPrimitive(2));
        assertThat(JournalCodec.CODEC.parse(JsonOps.INSTANCE, json).error()).isPresent();
    }
}
