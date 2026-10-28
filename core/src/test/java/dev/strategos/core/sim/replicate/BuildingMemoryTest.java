package dev.strategos.core.sim.replicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.build.BuildingStore;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BuildingMemoryTest {
    private static int handle(int index) {
        return Handle.of(BuildingStore.BASE + index, 1);
    }

    private static BuildingMemory.Seen seen(int handle, int owner, int health, long tick) {
        return new BuildingMemory.Seen(
                handle(handle), new TemplateId("custom:z_house"), owner, 8, 64, 12, 0, health, tick);
    }

    @Test
    void onlyPositiveObservationsChangeMemoryAndSnapshotsRemainUnchanged() {
        BuildingMemory initial = BuildingMemory.empty().observe(seen(7, 1, 255, 10));
        // A later observation elsewhere cannot refresh a hidden building's damage, ownership or destruction.
        BuildingMemory hidden = initial.observe(seen(9, 2, 125, 20));
        assertThat(hidden.buildings()).contains(seen(7, 1, 255, 10));
        BuildingMemory scouted = hidden.observe(seen(7, 2, 80, 30));
        assertThat(scouted.buildings()).contains(seen(7, 2, 80, 30)).doesNotContain(seen(7, 1, 255, 10));
        assertThat(initial.buildings()).containsExactly(seen(7, 1, 255, 10));
        BuildingMemory emptySite = scouted.scoutedEmpty(handle(7), 40);
        assertThat(emptySite.buildings()).containsExactly(seen(9, 2, 125, 20));
        assertThat(scouted.buildings()).hasSize(2);
        assertThatThrownBy(() -> emptySite.observe(seen(7, 1, 255, 35)))
                .as("old queued observation cannot resurrect a cleared mirage")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scouted.scoutedEmpty(handle(7), 20)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defensiveCopyAndCanonicalOrderGiveStableSnapshots() {
        List<BuildingMemory.Seen> input = new ArrayList<>(List.of(seen(9, 1, 200, 2), seen(2, 1, 100, 3)));
        BuildingMemory memory = new BuildingMemory(3, input);
        input.clear();
        assertThat(memory.buildings()).extracting(BuildingMemory.Seen::handle).containsExactly(handle(2), handle(9));
        assertThatThrownBy(() -> memory.buildings().clear()).isInstanceOf(UnsupportedOperationException.class);
        BuildingMemory reordered = new BuildingMemory(3, List.of(seen(2, 1, 100, 3), seen(9, 1, 200, 2)));
        assertThat(memory).isEqualTo(reordered);
        assertThat(BuildingMemory.CODEC.encodeStart(JsonOps.INSTANCE, memory).getOrThrow())
                .isEqualTo(BuildingMemory.CODEC
                        .encodeStart(JsonOps.INSTANCE, reordered)
                        .getOrThrow());
    }

    @Test
    void stableIdAndRemovalWatermarkSurviveSnapshotRoundTrip() {
        BuildingMemory memory =
                BuildingMemory.empty().observe(seen(7, 1, 125, 33)).scoutedEmpty(handle(99), 40);
        var json = BuildingMemory.CODEC.encodeStart(JsonOps.INSTANCE, memory).getOrThrow();
        assertThat(json.toString()).contains("custom:z_house").doesNotContain("netIndex");
        BuildingMemory restored =
                BuildingMemory.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertThat(restored).isEqualTo(memory);
        assertThatThrownBy(() -> restored.observe(seen(99, 1, 255, 35))).isInstanceOf(IllegalArgumentException.class);
        var cleared = memory.scoutedEmpty(handle(7), 50);
        var encoded =
                BuildingMemory.CODEC.encodeStart(JsonOps.INSTANCE, cleared).getOrThrow();
        assertThat(BuildingMemory.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow())
                .isEqualTo(cleared);
    }

    @Test
    void codecRejectsMalformedDuplicateFutureAndOversizedRecords() {
        BuildingMemory memory = BuildingMemory.empty().observe(seen(7, 1, 125, 33));
        var json = BuildingMemory.CODEC.encodeStart(JsonOps.INSTANCE, memory).getOrThrow();
        for (String malformed : List.of(
                json.toString().replace("\"health\":125", "\"health\":256"),
                json.toString().replace("\"owner\":1", "\"owner\":16"),
                json.toString().replace("\"rotation\":0", "\"rotation\":4"),
                json.toString().replace("\"handle\":" + handle(7), "\"handle\":0"),
                json.toString().replace("\"updatedAt\":33", "\"updatedAt\":-1"),
                json.toString().replace("\"tick\":33", "\"tick\":-1"),
                json.toString().replace("\"tick\":33", "\"tick\":34"),
                json.toString().replace("custom:z_house", "INVALID"))) {
            assertThat(BuildingMemory.CODEC
                            .parse(JsonOps.INSTANCE, JsonParser.parseString(malformed))
                            .error())
                    .as(malformed)
                    .isPresent();
        }
        var duplicate = json.deepCopy().getAsJsonObject();
        duplicate
                .getAsJsonArray("buildings")
                .add(duplicate.getAsJsonArray("buildings").get(0));
        assertThat(BuildingMemory.CODEC.parse(JsonOps.INSTANCE, duplicate).error())
                .isPresent();
        JsonArray oversized = new JsonArray();
        for (int i = 0; i <= BuildingMemory.MAX_BUILDINGS; i++) {
            var entry = json.getAsJsonObject()
                    .getAsJsonArray("buildings")
                    .get(0)
                    .deepCopy()
                    .getAsJsonObject();
            entry.addProperty("handle", handle(i));
            oversized.add(entry);
        }
        var tooMany = json.deepCopy().getAsJsonObject();
        tooMany.add("buildings", oversized);
        assertThat(BuildingMemory.CODEC.parse(JsonOps.INSTANCE, tooMany).error())
                .isPresent();
    }

    @Test
    void signedBuildingHandlesRoundTripWithoutDiscardingGenerationBits() {
        int signed = Handle.of(BuildingStore.BASE + 7, 2048);
        assertThat(signed).isNegative();
        var observation = new BuildingMemory.Seen(signed, new TemplateId("custom:z_house"), 1, 8, 64, 12, 0, 80, 3);
        var memory = BuildingMemory.empty().observe(observation).observe(seen(7, 1, 90, 3));
        var json = BuildingMemory.CODEC.encodeStart(JsonOps.INSTANCE, memory).getOrThrow();
        assertThat(BuildingMemory.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow())
                .isEqualTo(memory);
        assertThat(memory.buildings()).hasSize(2);
        assertThat(memory.scoutedEmpty(signed, 4).buildings()).containsExactly(seen(7, 1, 90, 3));
    }

    @Test
    void directConstructionRejectsInvalidSnapshots() {
        assertThatThrownBy(() -> new BuildingMemory(2, List.of(seen(1, 1, 200, 1), seen(1, 1, 100, 2))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BuildingMemory(1, List.of(seen(1, 1, 200, 2))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> seen(-1, 1, 200, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> seen(1, -1, 200, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> seen(1, 1, -1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> seen(1, 1, 200, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BuildingMemory.empty().scoutedEmpty(Handle.NONE, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
