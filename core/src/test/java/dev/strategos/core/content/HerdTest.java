package dev.strategos.core.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.command.ScenarioSetup;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.testkit.SimHarness;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ECO-10 in L1: a map's {@code herds} are optional and read back as written; they become Gaia's spawns in the written
 * order, the animals land within their spread of their centre at places that are the same every run, and a match with
 * herds restores to the live hash and replays to it.
 */
class HerdTest {
    private static final TemplateId BOAR = new TemplateId("strategos:unit/wild_boar");
    private static final List<Herd> HERDS = List.of(
            new Herd(BOAR, 20.5, 20.5, 3, 4), new Herd(BOAR, 100.5, 100.5, 3, 4), new Herd(BOAR, 20.5, 100.5, 2, 0));
    private static final Scenario SCENARIO = ScenarioTest.parse(
            "{ \"map\": \"strategos:test\", \"slots\": [ { \"entities\": [] }, { \"entities\": [] } ] }");

    private static SimHarness match() {
        SimHarness h =
                SimHarness.builder().size(128, 128).yBand(0, 31, 1).factions(2).build();
        h.step();
        for (Command c : ScenarioSetup.herds(SCENARIO, HERDS, TemplateSet.builtin())) {
            h.submit(PlayerSlot.ADMIN, c);
        }
        h.runTicks(5);
        return h;
    }

    private static List<double[]> places(SimHarness h) {
        MatchState s = KernelAccess.state(h.core());
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < s.entities().highWater(); i++) {
            if (s.entities().aliveAt(i)) {
                out.add(new double[] {
                    s.identity().faction(i), s.motion().x(i), s.motion().z(i)
                });
            }
        }
        return out;
    }

    @Test
    void herdsAreOptionalInTheManifestAndRoundTrip() {
        String json = "{ \"template\": \"strategos:unit/wild_boar\", \"x\": 12.5, \"z\": 30, \"count\": 4 }";
        Herd h =
                Herd.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
        assertThat(h).isEqualTo(new Herd(BOAR, 12.5, 30, 4, 2.0));
        assertThat(Herd.CODEC
                        .parse(
                                JsonOps.INSTANCE,
                                Herd.CODEC.encodeStart(JsonOps.INSTANCE, h).getOrThrow())
                        .getOrThrow())
                .isEqualTo(h);
        MapManifest bare = new MapManifest(
                "strategos:t",
                8,
                8,
                0,
                8,
                "minecraft:stone",
                8,
                List.of("minecraft:air"),
                List.of(),
                java.util.Map.of(),
                1,
                "0".repeat(64));
        assertThat(bare.herds()).isEmpty();
        var encoded = MapManifest.CODEC
                .encodeStart(JsonOps.INSTANCE, bare)
                .getOrThrow()
                .getAsJsonObject();
        assertThat(encoded.has("herds"))
                .as("an empty list is left out, as terrain jobs are")
                .isFalse();
        assertThat(MapManifest.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow())
                .isEqualTo(bare);
        encoded.remove("herds");
        assertThat(MapManifest.CODEC
                        .parse(JsonOps.INSTANCE, encoded)
                        .getOrThrow()
                        .herds())
                .as("a manifest written before herds has none")
                .isEmpty();
        encoded.add(
                "herds",
                JsonParser.parseString(
                        "[ { \"template\": \"strategos:unit/wild_boar\", \"x\": 9, \"z\": 1, \"count\": 1 } ]"));
        assertThat(MapManifest.CODEC.parse(JsonOps.INSTANCE, encoded).error())
                .as("a herd outside the map is refused")
                .isPresent();
    }

    @Test
    void herdsBecomeGaiaSpawnsInOrderAndOnlyForAnimals() {
        List<Command> commands = ScenarioSetup.herds(SCENARIO, HERDS, TemplateSet.builtin());
        assertThat(commands).hasSize(3);
        SpawnUnits first = (SpawnUnits) commands.get(0);
        assertThat(first.faction().value())
                .as("Gaia: the slot after the scenario's")
                .isEqualTo(2);
        assertThat(first.count()).isEqualTo(3);
        assertThat(first.spread()).isEqualTo(4);
        assertThatThrownBy(() -> ScenarioSetup.herds(
                        SCENARIO, List.of(new Herd(TemplateSet.WORKER, 1, 1, 1, 1)), TemplateSet.builtin()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theAnimalsStandWithinTheirSpreadAtTheSamePlacesEveryRun() {
        SimHarness h = match();
        List<double[]> a = places(h);
        assertThat(a).hasSize(8);
        MatchState s = KernelAccess.state(h.core());
        for (double[] p : a) {
            assertThat((int) p[0]).as("Gaia's").isEqualTo(s.gaia());
        }
        assertThat(a.subList(0, 3))
                .allSatisfy(
                        p -> assertThat(Math.hypot(p[1] - 20.5, p[2] - 20.5)).isLessThanOrEqualTo(4.01));
        assertThat(a.subList(6, 8))
                .allSatisfy(
                        p -> assertThat(Math.hypot(p[1] - 20.5, p[2] - 100.5)).isLessThan(0.5));
        List<double[]> b = places(match());
        for (int k = 0; k < a.size(); k++) {
            assertThat(b.get(k)).as("animal %d", k).containsExactly(a.get(k));
        }
        assertThat(match().stateHash()).isEqualTo(h.stateHash());
        for (int i = 0; i < s.entities().highWater(); i++) {
            if (s.entities().aliveAt(i)) {
                assertThat(Handle.index(s.entities().handleAt(i))).isEqualTo(i);
            }
        }
    }

    @Test
    void aMatchWithHerdsRestoresToTheLiveHashAndReplaysToIt() {
        SimHarness h = match();
        MatchSnapshot saved = MatchSnapshotCodec.decode(
                        new Dynamic<>(
                                JsonOps.INSTANCE,
                                MatchSnapshotCodec.encode(
                                        JsonOps.INSTANCE, h.core().snapshot())),
                        Migrations.production())
                .getOrThrow();
        SimHarness resumed = SimHarness.restore(saved, TemplateSet.builtin());
        assertThat(resumed.stateHash()).isEqualTo(h.stateHash());
        for (int i = 0; i < 20; i++) {
            h.step();
            resumed.step();
            assertThat(resumed.stateHash()).isEqualTo(h.stateHash());
        }
        var replay = h.replay();
        assertThat(replay.matches()).isTrue();
        assertThat(replay.finalHash()).isEqualTo(h.stateHash());
        h.assertInvariants();
    }
}
