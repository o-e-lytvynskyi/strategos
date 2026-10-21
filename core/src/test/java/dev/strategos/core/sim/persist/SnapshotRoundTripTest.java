package dev.strategos.core.sim.persist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CorePorts;
import dev.strategos.core.sim.InputEvent;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.TickInputs;
import dev.strategos.core.testkit.SimHarness;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SnapshotRoundTripTest {

    /** 500 units of two factions, some walking, some with pending orders. */
    private static SimHarness fiveHundredUnits() {
        SimHarness h = SimHarness.builder().size(64, 64).seed(13).build();
        int[] a = h.spawn(new FactionSlot(0), new Vec2d(16, 16), 250, 12.0);
        int[] b = h.spawn(new FactionSlot(1), new Vec2d(48, 48), 250, 12.0);
        h.submit(new PlayerSlot(0), Move.replace(UnitSet.of(Arrays.copyOf(a, 200)), new Vec2d(48, 16)));
        h.submit(new PlayerSlot(1), Move.replace(UnitSet.of(Arrays.copyOf(b, 100)), new Vec2d(16, 48)));
        h.runTicks(30);
        return h;
    }

    @Test
    void aFiveHundredUnitSnapshotSurvivesJsonWithAnEqualHash() {
        SimHarness h = fiveHundredUnits();
        MatchSnapshot snapshot = h.core().snapshot();
        assertThat(snapshot.units()).hasSize(500);

        JsonElement json = MatchSnapshotCodec.encode(JsonOps.INSTANCE, snapshot);
        assertThat(json.getAsJsonObject().get("v").getAsInt()).isEqualTo(SchemaVersion.CURRENT);
        MatchSnapshot decoded = MatchSnapshotCodec.decode(
                        new Dynamic<>(JsonOps.INSTANCE, json), Migrations.production())
                .getOrThrow();

        assertThat(decoded).isEqualTo(snapshot);
        MatchCore restored = MatchCore.restore(decoded, TemplateSet.builtin(), CorePorts.of(() -> 0L));
        assertThat(restored.stateHash()).isEqualTo(h.stateHash());
        assertThat(restored.queries().tick()).isEqualTo(h.tick());
    }

    @Test
    void theRestoredCoreKeepsTheSameHashForTwoHundredTicksOfInputs() {
        SimHarness h = fiveHundredUnits();
        MatchSnapshot decoded = MatchSnapshotCodec.decode(
                        new Dynamic<>(
                                JsonOps.INSTANCE,
                                MatchSnapshotCodec.encode(
                                        JsonOps.INSTANCE, h.core().snapshot())),
                        Migrations.production())
                .getOrThrow();
        MatchCore original = h.core();
        MatchCore restored = MatchCore.restore(decoded, TemplateSet.builtin(), CorePorts.of(() -> 0L));
        int[] faction0 = original.queries().unitHandlesOf(new FactionSlot(0));

        for (int i = 0; i < 200; i++) {
            long tick = original.queries().tick() + 1;
            List<InputEvent> inputs = i % 25 == 0
                    ? List.of(new CommandEnvelope(
                            new PlayerSlot(0),
                            i,
                            tick - 1,
                            Move.replace(UnitSet.of(Arrays.copyOf(faction0, 50)), new Vec2d(8 + i % 40, 50))))
                    : List.of();
            original.step(new TickInputs(tick, inputs));
            restored.step(new TickInputs(tick, inputs));
            assertThat(restored.stateHash()).as("tick %d", tick).isEqualTo(original.stateHash());
        }
    }

    @Test
    void slotTablesThatAreNoneDecodeToErrorsNeverExceptions() {
        SimHarness h = SimHarness.builder().build();
        h.spawn(new FactionSlot(0), new Vec2d(8, 8), 3, 2.0);
        JsonObject good =
                MatchSnapshotCodec.encode(JsonOps.INSTANCE, h.core().snapshot()).getAsJsonObject();
        JsonArray oversized = new JsonArray();
        for (int i = 0; i < Handle.MAX_INDEX + 2; i++) {
            oversized.add(1);
        }
        List<JsonObject> broken = List.of(
                withSlots(good, oversized, new JsonArray()),
                withSlots(good, ints(1, Handle.MAX_GENERATION + 1, 1), new JsonArray()),
                withSlots(good, ints(1, 1, 1), ints(5)),
                withSlots(good, ints(1, 1, 1, 1), ints(2, 1)));

        for (JsonObject json : broken) {
            assertThat(MatchSnapshotCodec.decode(new Dynamic<>(JsonOps.INSTANCE, json), Migrations.production())
                            .error())
                    .isPresent();
        }
    }

    private static JsonObject withSlots(JsonObject snapshot, JsonArray generations, JsonArray free) {
        JsonObject copy = snapshot.deepCopy();
        JsonObject slots = new JsonObject();
        slots.add("generations", generations);
        slots.add("free", free);
        copy.add("slots", slots);
        return copy;
    }

    private static JsonArray ints(int... values) {
        JsonArray array = new JsonArray();
        for (int v : values) {
            array.add(v);
        }
        return array;
    }

    @Test
    void restoreNamesAUnitWhoseTemplateTheSetLacks() {
        MatchSnapshot snapshot = fiveHundredUnits().core().snapshot();
        TemplateSet other = new TemplateSet(List.of(new UnitTemplate(
                new TemplateId("strategos:unit/other"), 0, "strategos:unit_humanoid", 0.25, 0.6f, 1.8f)));
        assertThatThrownBy(() -> MatchCore.restore(snapshot, other, CorePorts.of(() -> 0L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strategos:unit/debug_walker")
                .hasMessageContaining("#");
    }
}
