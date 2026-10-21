package dev.strategos.core.sim.persist;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.Hex64;
import dev.strategos.core.sim.CorePorts;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.testkit.Scenario;
import dev.strategos.core.testkit.Scenarios;
import dev.strategos.core.testkit.Seeds;
import dev.strategos.core.testkit.SimHarness;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * CORE-07.3: the released schema-1 save, the canary at tick 500 through {@code JsonOps}, still decodes to its recorded
 * hash, and a test-only step migrates it to version 2. {@code -Dstrategos.fixtures.update=true} writes the two files
 * once; they are released-schema fixtures, never changed or deleted, so an existing file is kept.
 */
class MatchSnapshotJsonFixtureTest {
    private static final String FIXTURE = "fixtures/match_v1.json";
    private static final String EXPECTED = "fixtures/match_v1.expected.json";
    private static final long AT_TICK = 500;

    @BeforeAll
    static void writeOnceWhenAsked() throws IOException {
        if (!Boolean.getBoolean("strategos.fixtures.update")) {
            return;
        }
        Path fixture = Path.of("src/test/resources", FIXTURE);
        Path expected = Path.of("src/test/resources", EXPECTED);
        if (Files.exists(fixture) || Files.exists(expected)) {
            return; // released: never rewritten
        }
        Scenario canary = Scenarios.canary(Seeds.first("canary"));
        SimHarness h = SimHarness.forScenario(canary);
        canary.setup(h);
        while (h.tick() < AT_TICK) {
            h.step();
            canary.onTick(h);
        }
        MatchSnapshot snapshot = h.core().snapshot();
        JsonObject facts = new JsonObject();
        facts.addProperty("v", SchemaVersion.CURRENT);
        facts.addProperty("tick", snapshot.tick());
        facts.addProperty("units", snapshot.units().size());
        facts.addProperty("stateHash", Hex64.format(h.stateHash()));
        Files.createDirectories(fixture.getParent());
        Files.writeString(
                fixture, pretty(MatchSnapshotCodec.encode(JsonOps.INSTANCE, snapshot)), StandardCharsets.UTF_8);
        Files.writeString(expected, pretty(facts), StandardCharsets.UTF_8);
    }

    private static String pretty(JsonElement json) {
        return new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n";
    }

    private static JsonElement resource(String name) throws IOException {
        Path written = Path.of("src/test/resources", name);
        if (Boolean.getBoolean("strategos.fixtures.update") && Files.exists(written)) {
            return JsonParser.parseString(
                    Files.readString(written, StandardCharsets.UTF_8)); // fresher than the classpath
        }
        try (InputStream in = MatchSnapshotJsonFixtureTest.class.getResourceAsStream("/" + name)) {
            assertThat(in).as("the committed fixture " + name).isNotNull();
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void theReleasedVersion1SaveDecodesToItsRecordedHash() throws IOException {
        JsonElement json = resource(FIXTURE);
        JsonObject expected = resource(EXPECTED).getAsJsonObject();
        assertThat(json.getAsJsonObject().get("v").getAsInt()).isEqualTo(1);

        MatchSnapshot snapshot = MatchSnapshotCodec.decode(
                        new Dynamic<>(JsonOps.INSTANCE, json), Migrations.production())
                .getOrThrow();
        MatchCore core = MatchCore.restore(snapshot, TemplateSet.builtin(), CorePorts.of(() -> 0L));

        assertThat(core.queries().tick())
                .isEqualTo(expected.get("tick").getAsLong())
                .isEqualTo(AT_TICK);
        assertThat(core.queries().unitCount()).isEqualTo(expected.get("units").getAsInt());
        assertThat(Hex64.format(core.stateHash()))
                .isEqualTo(expected.get("stateHash").getAsString());
        assertThat(snapshot.units())
                .as("the fixture holds walking units, with targets")
                .anyMatch(u -> u.target() != null);
    }

    @Test
    void aTestOnlyStepMigratesItToVersion2() throws IOException {
        JsonObject original = resource(FIXTURE).getAsJsonObject();
        double floorY = original.getAsJsonObject("config")
                .getAsJsonObject("bounds")
                .get("flatFloorY")
                .getAsDouble();
        // The kind of step M1 brings when terrain replaces the flat floor: a field moves out of the bounds.
        MigrationStep floorToTerrain = new MigrationStep() {
            @Override
            public int from() {
                return 1;
            }

            @Override
            public <T> Dynamic<T> apply(Dynamic<T> input) {
                Dynamic<T> bounds = input.get("config").get("bounds").orElseEmptyMap();
                Dynamic<T> floor = bounds.get("flatFloorY").orElseEmptyMap();
                Dynamic<T> config = input.get("config")
                        .orElseEmptyMap()
                        .set("bounds", bounds.remove("flatFloorY"))
                        .set("terrain", input.emptyMap().set("flatFloorY", floor));
                return input.set("config", config);
            }
        };

        JsonObject migrated = new Migrations(List.of(floorToTerrain))
                .migrate(new Dynamic<>(JsonOps.INSTANCE, original.deepCopy()), 2)
                .getOrThrow()
                .getValue()
                .getAsJsonObject();

        assertThat(migrated.get("v").getAsInt()).isEqualTo(2);
        JsonObject config = migrated.getAsJsonObject("config");
        assertThat(config.getAsJsonObject("bounds").has("flatFloorY")).isFalse();
        assertThat(config.getAsJsonObject("terrain").get("flatFloorY").getAsDouble())
                .isEqualTo(floorY);
        assertThat(migrated.get("units")).as("the rest is carried over").isEqualTo(original.get("units"));
        assertThat(migrated.get("tick")).isEqualTo(original.get("tick"));
    }
}
