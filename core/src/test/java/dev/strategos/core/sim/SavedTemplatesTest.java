package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.diag.MatchLog;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.testkit.SimHarness;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SavedTemplatesTest {
    private static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    private static final TemplateId CUSTOM_HOUSE = new TemplateId("test:z_house");
    private static final TemplateId CUSTOM_UNIT = new TemplateId("test:z_unit");
    private static final int FIRST = TemplateSet.builtin().all().stream()
                    .mapToInt(UnitTemplate::netIndex)
                    .max()
                    .orElseThrow()
            + 1;

    @Test
    void diskRoundTripRemapsBuildingsAndEveryQueuedProduct() {
        MatchState state = state(custom(false));
        int handle = building(state, CUSTOM_HOUSE);
        state.buildings().enqueue(handle, FIRST + 1, 5);
        state.buildings().enqueue(handle, FIRST + 1, 2);
        state.buildings().setTrained(handle, 7);
        MatchSnapshot saved = roundTrip(Snapshots.take(state));
        var restored = SavedTemplates.buildings(saved, custom(true)).getFirst();
        assertThat(restored.template()).isEqualTo(FIRST + 1);
        assertThat(restored.production().queue()).containsExactly(FIRST + 2, FIRST + 2);
        assertThat(restored.production().batches()).containsExactly(5, 2);
        assertThat(restored.production().trained()).isEqualTo(7);
    }

    @Test
    void missingBuildingOrQueuedTemplateRefusesRestore() {
        MatchState state = state(custom(false));
        int handle = building(state, CUSTOM_HOUSE);
        state.buildings().enqueue(handle, FIRST + 1);
        MatchSnapshot saved = roundTrip(Snapshots.take(state));
        assertThatThrownBy(() -> SavedTemplates.buildings(saved, TemplateSet.builtin()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(CUSTOM_HOUSE.value());
        TemplateSet missingProduct = without(custom(false), CUSTOM_UNIT);
        assertThatThrownBy(() -> SavedTemplates.buildings(saved, missingProduct))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(CUSTOM_UNIT.value());
    }

    @Test
    void legacyBuiltinIndicesLoadButAmbiguousCustomIndicesRefuse() {
        MatchState builtin = state(TemplateSet.builtin());
        building(builtin, new TemplateId("strategos:building/civic_centre"));
        assertThat(SavedTemplates.buildings(legacy(Snapshots.take(builtin)), custom(true)))
                .hasSize(1);
        MatchState custom = state(custom(false));
        building(custom, CUSTOM_HOUSE);
        assertThatThrownBy(() -> SavedTemplates.buildings(legacy(Snapshots.take(custom)), custom(true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ambiguous template index");
        MatchState customQueue = state(custom(false));
        int handle = building(customQueue, new TemplateId("strategos:building/civic_centre"));
        customQueue.buildings().enqueue(handle, FIRST + 1);
        MatchSnapshot queued = legacy(Snapshots.take(customQueue));
        assertThatThrownBy(() -> SavedTemplates.buildings(queued, custom(true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ambiguous template index");
    }

    @Test
    void reloadRejectsMissingQueuedProductAndChangedIndexAtomically() {
        MatchState state = state(custom(false));
        int handle = building(state, HOUSE);
        state.buildings().enqueue(handle, FIRST + 1);
        TemplateSet before = state.templates();
        state.swapTemplates(without(before, CUSTOM_UNIT));
        assertThat(state.templates()).isSameAs(before);
        assertThat(state.buildings().queue(handle)).containsExactly(FIRST + 1);
        state.swapTemplates(custom(true));
        assertThat(state.templates()).isSameAs(before);
        state.swapTemplates(before);
        assertThat(state.templates()).isSameAs(before);
    }

    @Test
    void equalContentHashesCannotRecoverLegacyCustomIndices() {
        TemplateSet original = custom(false);
        var flats = original.all().stream()
                .map(t -> new TemplateCompiler.Flat(
                        t.id(),
                        t.id().equals(CUSTOM_HOUSE) ? FIRST + 1 : t.id().equals(CUSTOM_UNIT) ? FIRST : t.netIndex(),
                        original.flattened(t.id())))
                .toList();
        TemplateSet reordered = TemplateCompiler.fromFlattened(flats).set();
        assertThat(reordered.hash()).isEqualTo(original.hash());
        MatchState state = state(original);
        building(state, CUSTOM_HOUSE);
        MatchSnapshot snapshot = Snapshots.take(state);
        assertThat(SavedTemplates.buildings(roundTrip(snapshot), reordered)
                        .getFirst()
                        .template())
                .isEqualTo(FIRST + 1);
        assertThatThrownBy(() -> SavedTemplates.buildings(legacy(snapshot), reordered))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ambiguous template index");
    }

    @Test
    void legacyIndicesLaterClaimedByBuiltinsNeverChangeIdentity() {
        // Only the original common prefix 0..4 can be recovered across every building-save era.
        // M4 and M5 appended builtins where earlier sessions could have assigned custom ids.
        for (int index : List.of(7, 14, 16, 17)) {
            TemplateSet builtin = TemplateSet.builtin();
            List<TemplateCompiler.Flat> flats = new ArrayList<>();
            for (UnitTemplate t : builtin.all()) {
                if (t.netIndex() < 5) {
                    flats.add(new TemplateCompiler.Flat(t.id(), t.netIndex(), builtin.flattened(t.id())));
                }
            }
            TemplateId custom = new TemplateId("test:old_custom");
            boolean isBuilding = builtin.byIndex(index).isBuilding();
            flats.add(new TemplateCompiler.Flat(
                    custom, index, builtin.flattened(isBuilding ? HOUSE : TemplateSet.INFANTRY)));
            MatchState state = state(TemplateCompiler.fromFlattened(flats).set());
            int handle = building(state, isBuilding ? custom : new TemplateId("strategos:building/civic_centre"));
            if (!isBuilding) {
                state.buildings().enqueue(handle, index);
            }
            MatchSnapshot old = legacy(Snapshots.take(state));
            assertThatThrownBy(() -> SavedTemplates.buildings(roundTrip(old), builtin))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ambiguous template index");
        }
    }

    @Test
    void earliestM3HouseCannotSilentlyBecomeAField() {
        TemplateSet builtin = TemplateSet.builtin();
        List<TemplateCompiler.Flat> flats = new ArrayList<>();
        for (UnitTemplate t : builtin.all()) {
            if (t.netIndex() < 5) {
                flats.add(new TemplateCompiler.Flat(t.id(), t.netIndex(), builtin.flattened(t.id())));
            }
        }
        flats.add(new TemplateCompiler.Flat(HOUSE, 5, builtin.flattened(HOUSE)));
        MatchState state = state(TemplateCompiler.fromFlattened(flats).set());
        building(state, HOUSE);
        MatchSnapshot old = legacy(Snapshots.take(state));
        assertThatThrownBy(() -> SavedTemplates.buildings(roundTrip(old), builtin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ambiguous template index");
    }

    @Test
    void schemaThreeCannotMasqueradeAsLegacyWithoutBindings() {
        MatchState state = state(TemplateSet.builtin());
        building(state, HOUSE);
        for (boolean empty : List.of(false, true)) {
            var json = MatchSnapshotCodec.encode(JsonOps.INSTANCE, Snapshots.take(state))
                    .getAsJsonObject();
            if (empty) {
                json.add("templateBindings", new com.google.gson.JsonArray());
            } else {
                json.remove("templateBindings");
            }
            var decoded = MatchSnapshotCodec.decode(new Dynamic<>(JsonOps.INSTANCE, json), Migrations.production());
            assertThat(decoded.error()).isPresent();
            assertThat(decoded.error().orElseThrow().message()).contains("bindings");
        }
    }

    @Test
    void reloadGuardsTemplateBackedNodesAndKindChanges() {
        MatchState state = state(TemplateSet.builtin());
        TemplateId deposit = new TemplateId("strategos:building/quarry");
        int node = state.nodes()
                .add(dev.strategos.core.sim.nav.MaterialKind.STONE, 0, 0, 1, 1, 8, 8, 1, new long[0], new int[0]);
        state.nodes().setTemplate(node, state.templates().byId(deposit).netIndex());
        TemplateSet before = state.templates();
        assertThat(state.templateSwapProblem(without(before, deposit))).contains(deposit.value());
        state.swapTemplates(without(before, deposit));
        assertThat(state.templates()).isSameAs(before);
        assertThat(state.nodes().template(node)).isEqualTo(before.byId(deposit).netIndex());
        var changed = before.all().stream()
                .map(t -> new TemplateCompiler.Flat(
                        t.id(), t.netIndex(), before.flattened(t.id().equals(deposit) ? TemplateSet.INFANTRY : t.id())))
                .toList();
        TemplateSet wrongKind = TemplateCompiler.fromFlattened(changed).set();
        assertThat(state.templateSwapProblem(wrongKind)).contains("kind");
        state.swapTemplates(wrongKind);
        assertThat(state.templates()).isSameAs(before);
    }

    private static MatchSnapshot legacy(MatchSnapshot saved) {
        var json = MatchSnapshotCodec.encode(JsonOps.INSTANCE, saved).getAsJsonObject();
        json.remove("templateBindings");
        json.addProperty("v", 2);
        return MatchSnapshotCodec.decode(new Dynamic<>(JsonOps.INSTANCE, json), Migrations.production())
                .getOrThrow();
    }

    private static MatchSnapshot roundTrip(MatchSnapshot saved) {
        return MatchSnapshotCodec.decode(
                        new Dynamic<>(JsonOps.INSTANCE, MatchSnapshotCodec.encode(JsonOps.INSTANCE, saved)),
                        Migrations.production())
                .getOrThrow();
    }

    private static int building(MatchState state, TemplateId id) {
        var template = state.templates().byId(id);
        assertThat(template).isNotNull();
        return state.buildings()
                .create(
                        template.netIndex(),
                        0,
                        new Footprint(8, 8, 0, 3, 3),
                        1,
                        BuildingStore.COMPLETE,
                        BuildingStore.FULL);
    }

    private static MatchState state(TemplateSet set) {
        return new MatchState(
                SimHarness.builder().build().core().snapshot().config(),
                set,
                new EntityStore(),
                MatchLog.forSim(new MatchId(1)));
    }

    private static TemplateSet custom(boolean inserted) {
        TemplateSet builtin = TemplateSet.builtin();
        List<TemplateCompiler.Flat> flats = new ArrayList<>();
        for (UnitTemplate t : builtin.all()) {
            flats.add(new TemplateCompiler.Flat(t.id(), t.netIndex(), builtin.flattened(t.id())));
        }
        if (inserted) {
            flats.add(new TemplateCompiler.Flat(new TemplateId("test:a_house"), FIRST, builtin.flattened(HOUSE)));
        }
        flats.add(new TemplateCompiler.Flat(CUSTOM_HOUSE, FIRST + (inserted ? 1 : 0), builtin.flattened(HOUSE)));
        flats.add(new TemplateCompiler.Flat(
                CUSTOM_UNIT, FIRST + (inserted ? 2 : 1), builtin.flattened(TemplateSet.INFANTRY)));
        var result = TemplateCompiler.fromFlattened(flats);
        assertThat(result.ok()).isTrue();
        return result.set();
    }

    private static TemplateSet without(TemplateSet set, TemplateId omitted) {
        return TemplateCompiler.fromFlattened(set.all().stream()
                        .filter(t -> !t.id().equals(omitted))
                        .map(t -> new TemplateCompiler.Flat(t.id(), t.netIndex(), set.flattened(t.id())))
                        .toList())
                .set();
    }
}
