package dev.strategos.core.sim.unitai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.AiStateStore;
import dev.strategos.core.sim.CorePorts;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.UnitRecord;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.testkit.SimHarness;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * UNIT-02.2 (spec §7.2): a unit's machine round-trips through the snapshot codec, its leaf by name and its data words
 * once set, with an equal snapshot, an equal hash and equal restored stores. A unit whose words are 0 saves only its
 * {@code "state"}, as in M0; every leaf resolves by name and by code; an unknown or group state and words that are not
 * two do not decode, and a restore refuses a state that is no leaf.
 */
class HfsmPersistenceTest {
    @Test
    void dataWordsRoundTripWithAnEqualHash() {
        SimHarness h = walkers();
        int[] units = h.queries().unitHandles();
        AiStateStore ai = KernelAccess.state(h.core()).ai();
        long plainHash = h.stateHash();
        ai.setData(Handle.index(units[0]), 7, -1);
        ai.setData(Handle.index(units[2]), 0, Integer.MAX_VALUE);
        MatchSnapshot snapshot = h.core().snapshot();

        MatchSnapshot decoded =
                decode(MatchSnapshotCodec.encode(JsonOps.INSTANCE, snapshot)).getOrThrow();
        MatchCore restored = MatchCore.restore(decoded, TemplateSet.builtin(), CorePorts.of(() -> 0L));

        assertThat(h.stateHash()).as("the words are hashed once set").isNotEqualTo(plainHash);
        assertThat(decoded).isEqualTo(snapshot);
        assertThat(restored.stateHash()).isEqualTo(h.stateHash());
        AiStateStore back = KernelAccess.state(restored).ai();
        for (int unit : units) {
            int i = Handle.index(unit);
            assertThat(back.code(i)).isEqualTo(ai.code(i));
            assertThat(back.state(i)).isEqualTo(ai.state(i));
            assertThat(back.data0(i)).isEqualTo(ai.data0(i));
            assertThat(back.data1(i)).isEqualTo(ai.data1(i));
        }
    }

    @Test
    void aPlainUnitSavesOnlyItsState() {
        SimHarness h = walkers();
        JsonArray units =
                units(MatchSnapshotCodec.encode(JsonOps.INSTANCE, h.core().snapshot()));

        List<String> states = new ArrayList<>();
        for (JsonElement unit : units) {
            assertThat(unit.getAsJsonObject().has("data"))
                    .as("a unit with both words 0")
                    .isFalse();
            states.add(unit.getAsJsonObject().get("state").getAsString());
        }
        assertThat(states).containsOnly("idle", "walking").contains("idle", "walking");
    }

    @Test
    void everyLeafResolvesByNameAndCode() {
        for (StateId leaf : UnitAi.leaves()) {
            assertThat(UnitAi.leaf(leaf.name())).isEqualTo(leaf);
            assertThat(UnitAi.leaf(leaf.code())).isEqualTo(leaf);
        }
        assertThat(UnitAi.leaf("individual"))
                .as("a state with children is no leaf")
                .isNull();
        assertThatThrownBy(() -> UnitAi.leaf(UnitAi.INDIVIDUAL.code())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anUnknownStateOrBadWordsDoNotDecode() {
        SimHarness h = walkers();
        JsonElement good = MatchSnapshotCodec.encode(JsonOps.INSTANCE, h.core().snapshot());
        List<Consumer<JsonObject>> breaks = List.of(
                unit -> unit.addProperty("state", "dancing"),
                unit -> unit.addProperty("state", "individual"),
                unit -> unit.add("data", words(1)),
                unit -> unit.add("data", words(1, 2, 3)),
                unit -> unit.add("data", new JsonPrimitive("two")));
        for (Consumer<JsonObject> spoil : breaks) {
            JsonElement bad = good.deepCopy();
            spoil.accept(units(bad).get(0).getAsJsonObject());

            assertThat(decode(bad).error()).as("decoding " + units(bad).get(0)).isPresent();
        }
        assertThat(decode(good).error()).isEmpty();
    }

    @Test
    void aRestoreRefusesAStateThatIsNoLeaf() {
        MatchSnapshot snapshot = walkers().core().snapshot();
        List<UnitRecord> units = new ArrayList<>(snapshot.units());
        UnitRecord first = units.get(0);
        units.set(
                0,
                new UnitRecord(
                        first.handle(),
                        first.template(),
                        first.faction(),
                        first.x(),
                        first.y(),
                        first.z(),
                        first.yaw(),
                        new AiStateStore.Saved(UnitAi.INDIVIDUAL, 0, 0),
                        first.target(),
                        first.path(),
                        first.motion()));
        MatchSnapshot bad = new MatchSnapshot(
                snapshot.config(),
                snapshot.tick(),
                snapshot.templateSetHash(),
                snapshot.slots(),
                units,
                snapshot.pendingCommands(),
                snapshot.pages(),
                snapshot.paths());

        assertThatThrownBy(() -> MatchCore.restore(bad, TemplateSet.builtin(), CorePorts.of(() -> 0L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("individual");
    }

    /** Four walkers of faction 0, two of them walking and two idle, after 10 ticks. */
    private static SimHarness walkers() {
        SimHarness h = SimHarness.builder().build();
        int[] units = h.spawn(new FactionSlot(0), new Vec2d(8, 8), 4, 2);
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(units[0], units[1]), new Vec2d(28, 28)));
        h.runTicks(10);
        return h;
    }

    private static DataResult<MatchSnapshot> decode(JsonElement json) {
        return MatchSnapshotCodec.decode(new Dynamic<>(JsonOps.INSTANCE, json), Migrations.production());
    }

    private static JsonArray units(JsonElement snapshot) {
        return snapshot.getAsJsonObject().getAsJsonArray("units");
    }

    private static JsonArray words(int... values) {
        JsonArray array = new JsonArray();
        for (int value : values) {
            array.add(value);
        }
        return array;
    }
}
