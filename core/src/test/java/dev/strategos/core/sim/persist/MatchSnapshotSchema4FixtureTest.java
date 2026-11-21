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
import dev.strategos.core.testkit.FixtureMatches;
import dev.strategos.core.testkit.SimHarness;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Schema 4 (#25): {@link FixtureMatches#schema3} at its tick through {@code JsonOps}
 * still decodes to its recorded hash, with its buildings, production queues and stable template bindings. {@code
 * -Dstrategos.fixtures.update=true} writes the two files once; they are released-schema fixtures, never changed or
 * deleted, so an existing file is kept.
 */
class MatchSnapshotSchema4FixtureTest {
    private static final String FIXTURE = "fixtures/match_v4.json";
    private static final String EXPECTED = "fixtures/match_v4.expected.json";

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
        SimHarness h = FixtureMatches.schema3();
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
            return JsonParser.parseString(Files.readString(written, StandardCharsets.UTF_8));
        }
        try (InputStream in = MatchSnapshotSchema4FixtureTest.class.getResourceAsStream("/" + name)) {
            assertThat(in).as("the committed fixture " + name).isNotNull();
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void theReleasedVersion4SaveDecodesToItsRecordedHash() throws IOException {
        JsonElement json = resource(FIXTURE);
        JsonObject expected = resource(EXPECTED).getAsJsonObject();
        assertThat(json.getAsJsonObject().get("v").getAsInt()).isEqualTo(4);

        MatchSnapshot snapshot = MatchSnapshotCodec.decode(
                        new Dynamic<>(JsonOps.INSTANCE, json), Migrations.production())
                .getOrThrow();
        MatchCore core = MatchCore.restore(snapshot, TemplateSet.builtin(), CorePorts.of(() -> 0L));

        assertThat(core.queries().tick())
                .isEqualTo(expected.get("tick").getAsLong())
                .isEqualTo(FixtureMatches.SCHEMA_3_TICK);
        assertThat(core.queries().unitCount()).isEqualTo(expected.get("units").getAsInt());
        assertThat(Hex64.format(core.stateHash()))
                .isEqualTo(expected.get("stateHash").getAsString());
        assertThat(snapshot.templateBindings()).isNotEmpty();
        assertThat(core.queries().buildings()).hasSize(2);
        assertThat(core.queries().buildings().getFirst().queue()).hasSize(2);
        assertThat(core.queries().buildings().getFirst().trained()).isPositive();
    }
}
