package dev.strategos.core.sim.garrison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.AllClear;
import dev.strategos.core.protocol.Garrison;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.RingBell;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.Ungarrison;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.combat.Damage;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.testkit.Seeds;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * Garrisons are saved and hashed only once a unit is inside a building, so every earlier save and journal keeps its
 * bytes; a match saved with units inside resumes with the same hashes, and so does its journal's replay.
 */
class GarrisonPersistenceTest {
    private static final TemplateId CENTRE = new TemplateId("strategos:building/civic_centre");

    private final SimHarness h = SimHarness.builder()
            .seed(Seeds.first("canary"))
            .size(96, 64)
            .yBand(0, 31, 1)
            .factions(3)
            .build();
    private final MatchState s;

    GarrisonPersistenceTest() {
        h.publishAll(VoxelFixtures.flat(96, 64, 32, 1));
        h.step();
        s = KernelAccess.state(h.core());
    }

    private static MatchSnapshot roundTrip(MatchSnapshot snapshot) {
        return MatchSnapshotCodec.decode(
                        new Dynamic<>(JsonOps.INSTANCE, MatchSnapshotCodec.encode(JsonOps.INSTANCE, snapshot)),
                        Migrations.production())
                .getOrThrow();
    }

    private int centre() {
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, new FactionSlot(0), 40, 30, 0));
        h.step();
        int[] handles = s.buildings().handles();
        return handles[handles.length - 1];
    }

    private int[] workers(int count) {
        h.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.WORKER, new FactionSlot(0), new Vec2d(30.5, 46.5), count, 1.5));
        return h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .toArray();
    }

    @Test
    void aMatchWithoutGarrisonsSavesAndHashesAsBefore() {
        centre();
        workers(3);
        h.runTicks(5);
        JsonObject json =
                MatchSnapshotCodec.encode(JsonOps.INSTANCE, h.core().snapshot()).getAsJsonObject();
        assertThat(json.has("play") ? json.getAsJsonObject("play").has("garrisons") : false)
                .as("no garrison rows while nobody is inside")
                .isFalse();
        assertThat(h.core().snapshot().play().garrisons()).isEmpty();
        long hash = h.stateHash();
        s.garrison().occupants(1); // reading is not using
        assertThat(h.stateHash()).isEqualTo(hash);
    }

    @Test
    void unitsInsideRoundTripAndResumeWithTheSameHashes() {
        int centre = centre();
        int[] workers = workers(4);
        long before = h.stateHash();
        h.submit(new PlayerSlot(0), new Garrison(UnitSet.of(workers), centre));
        assertThat(h.runUntil(() -> s.garrison().count(centre) == 4, 400)).isTrue();
        h.runTicks(3);
        assertThat(h.stateHash()).isNotEqualTo(before);

        MatchSnapshot saved = roundTrip(h.core().snapshot());
        assertThat(saved.play().garrisons()).hasSize(4);
        SimHarness back = SimHarness.restore(saved, TemplateSet.builtin());
        assertThat(back.stateHash()).isEqualTo(h.stateHash());
        MatchState resumed = KernelAccess.state(back.core());
        assertThat(resumed.garrison().occupants(centre)).isEqualTo(s.garrison().occupants(centre));
        assertThat(resumed.spatial().units().count())
                .as("still out of the index")
                .isZero();
        for (int w : workers) {
            assertThat(resumed.ai().state(Handle.index(w))).isEqualTo(UnitState.GARRISONED);
        }

        Ungarrison out = new Ungarrison(centre, UnitSet.of(workers[1], workers[3]));
        h.submit(new PlayerSlot(0), out);
        back.submit(new PlayerSlot(0), out);
        for (int i = 0; i < 6; i++) {
            h.step();
            back.step();
            assertThat(back.stateHash()).isEqualTo(h.stateHash());
        }
        assertThat(resumed.garrison().count(centre)).isEqualTo(2);
        assertThat(resumed.spatial().units().count()).isEqualTo(2);
    }

    @Test
    void theJournalOfAGarrisonedMatchReplaysToTheSameHash() {
        int centre = centre();
        int[] workers = workers(3);
        h.submit(new PlayerSlot(0), new Garrison(UnitSet.of(workers), centre));
        assertThat(h.runUntil(() -> s.garrison().count(centre) == 3, 400)).isTrue();
        h.submit(new PlayerSlot(0), new Ungarrison(centre, UnitSet.of(workers[0])));
        h.runTicks(20);
        assertThat(h.replay().matches()).isTrue();
    }

    private MatchSnapshot mutated(MatchSnapshot snapshot, Consumer<JsonArray> edit) {
        JsonObject json = MatchSnapshotCodec.encode(JsonOps.INSTANCE, snapshot).getAsJsonObject();
        edit.accept(json.getAsJsonObject("play").getAsJsonArray("garrisons"));
        return MatchSnapshotCodec.decode(new Dynamic<>(JsonOps.INSTANCE, json), Migrations.production())
                .getOrThrow();
    }

    @Test
    void aSaveWithRowsThatCannotHoldIsRefusedAndSoIsOneMissingARow() {
        int centre = centre();
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, new FactionSlot(1), 70, 30, 0));
        h.step();
        int foreign = s.buildings().handles()[s.buildings().handles().length - 1];
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, new FactionSlot(0), 10, 10, 0));
        h.step();
        int unfinished = s.buildings().handles()[s.buildings().handles().length - 1];
        s.buildings().setState(unfinished, dev.strategos.core.sim.build.BuildingStore.CONSTRUCTING, 0);
        int[] workers = workers(3);
        h.submit(new PlayerSlot(0), new Garrison(UnitSet.of(workers[0], workers[1]), centre));
        assertThat(h.runUntil(() -> s.garrison().count(centre) == 2, 400)).isTrue();
        MatchSnapshot saved = h.core().snapshot();
        int inside = s.garrison().occupants(centre)[0];
        TemplateSet templates = TemplateSet.builtin();

        assertThat(SimHarness.restore(mutated(saved, a -> {}), templates).stateHash())
                .isEqualTo(h.stateHash());
        List<Consumer<JsonArray>> bad = List.of(
                a -> a.get(0).getAsJsonObject().addProperty("unit", Handle.of(400, 1)),
                a -> a.get(0)
                        .getAsJsonObject()
                        .addProperty("building", dev.strategos.core.sim.build.BuildingStore.BASE + 9),
                a -> a.get(0).getAsJsonObject().addProperty("building", unfinished),
                a -> a.get(0).getAsJsonObject().addProperty("building", foreign),
                a -> a.get(0).getAsJsonObject().addProperty("unit", workers[2]),
                a -> a.add(a.get(0).deepCopy()),
                a -> a.remove(0));
        for (int k = 0; k < bad.size(); k++) {
            Consumer<JsonArray> edit = bad.get(k);
            assertThatThrownBy(() -> SimHarness.restore(mutated(saved, edit), templates))
                    .as("case " + k + " of unit " + inside)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void aSavedWalkWithAnOrderBehindItResumesWithTheSameHashes() {
        int centre = centre();
        int worker = workers(1)[0];
        h.submit(new PlayerSlot(0), new Garrison(UnitSet.of(worker), centre));
        h.submit(new PlayerSlot(0), new Move(UnitSet.of(worker), new Vec2d(20.5, 20.5), QueueMode.QUEUE, 0));
        h.runTicks(4);
        assertThat(s.garrison().empty()).as("still on the way").isTrue();
        SimHarness back = SimHarness.restore(roundTrip(h.core().snapshot()), TemplateSet.builtin());
        assertThat(back.stateHash()).isEqualTo(h.stateHash());
        back.publishAll(VoxelFixtures.flat(96, 64, 32, 1)); // the adapter publishes the terrain again
        for (int i = 0; i < 300 && s.garrison().empty(); i++) {
            h.step();
            back.step();
            assertThat(back.stateHash()).isEqualTo(h.stateHash());
        }
        assertThat(s.garrison().count(centre)).isEqualTo(1);
        assertThat(s.orders().size(Handle.index(worker)))
                .as("the move waits behind")
                .isEqualTo(1);
    }

    @Test
    void aCentreFallingOnTheOccupantsResumesWithTheSameHashes() {
        int centre = centre();
        int[] workers = workers(4);
        h.submit(new PlayerSlot(0), new Garrison(UnitSet.of(workers), centre));
        assertThat(h.runUntil(() -> s.garrison().count(centre) == 4, 400)).isTrue();
        s.queueDamage(new Damage(centre, 0, 0, 0, 0, 0, List.of(), true));
        h.step(); // the fall and the ejection happen in COMBAT, after the grid was built
        assertThat(s.garrison().empty()).isTrue();
        assertThat(s.spatial().units().count()).as("the grid has them at once").isEqualTo(4);

        SimHarness back = SimHarness.restore(roundTrip(h.core().snapshot()), TemplateSet.builtin());
        assertThat(back.stateHash()).isEqualTo(h.stateHash());
        assertThat(KernelAccess.state(back.core()).spatial().units().count()).isEqualTo(4);
        back.publishAll(VoxelFixtures.flat(96, 64, 32, 1));
        for (int i = 0; i < 40; i++) {
            h.step();
            back.step();
            assertThat(back.stateHash()).isEqualTo(h.stateHash());
        }
    }

    @Test
    void theBellsMarkIsSavedAndHashedOnlyOnceTheBellCalledSomeoneIn() {
        int centre = centre();
        int[] workers = workers(3);
        h.submit(new PlayerSlot(0), new Garrison(UnitSet.of(workers[0]), centre));
        assertThat(h.runUntil(() -> s.garrison().count(centre) == 1, 400)).isTrue();
        long plain = h.stateHash();
        JsonObject json =
                MatchSnapshotCodec.encode(JsonOps.INSTANCE, h.core().snapshot()).getAsJsonObject();
        assertThat(json.getAsJsonObject("play")
                        .getAsJsonArray("garrisons")
                        .get(0)
                        .getAsJsonObject()
                        .has("bell"))
                .as("a unit that went in on its own order has no mark in the save")
                .isFalse();

        h.submit(new PlayerSlot(0), new RingBell(centre));
        assertThat(h.runUntil(() -> s.garrison().count(centre) == 3, 400)).isTrue();
        h.runTicks(3);
        MatchSnapshot saved = roundTrip(h.core().snapshot());
        assertThat(saved.play().garrisons())
                .extracting(GarrisonStore.Saved::bell)
                .containsExactly(false, true, true);
        SimHarness back = SimHarness.restore(saved, TemplateSet.builtin());
        assertThat(back.stateHash()).isEqualTo(h.stateHash());
        assertThat(KernelAccess.state(back.core()).garrison().belled(Handle.index(workers[1])))
                .isTrue();

        back.publishAll(VoxelFixtures.flat(96, 64, 32, 1)); // the adapter publishes the terrain again
        AllClear clear = new AllClear(centre);
        h.submit(new PlayerSlot(0), clear);
        back.submit(new PlayerSlot(0), clear);
        h.step();
        back.step();
        assertThat(back.stateHash()).isEqualTo(h.stateHash());
        assertThat(s.garrison().occupants(centre)).containsExactly(workers[0]);
        assertThat(h.stateHash()).isNotEqualTo(plain); // the others are out and walking: another state
    }

    @Test
    void aMarkOnAPlainRowIsTheSameAsNoneUntilSet() {
        int centre = centre();
        int worker = workers(1)[0];
        h.submit(new PlayerSlot(0), new Garrison(UnitSet.of(worker), centre));
        assertThat(h.runUntil(() -> s.garrison().count(centre) == 1, 400)).isTrue();
        h.runTicks(2);
        long plain = h.stateHash();
        assertThat(s.garrison().belled(Handle.index(worker))).isFalse();
        s.garrison().remove(Handle.index(worker));
        s.garrison().add(Handle.index(worker), worker, centre, true);
        assertThat(h.stateHash()).as("the mark is part of the state").isNotEqualTo(plain);
    }
}
