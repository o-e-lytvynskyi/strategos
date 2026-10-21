package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.shared.Hex64;
import dev.strategos.core.sim.journal.Journal;
import dev.strategos.core.testkit.Scenario;
import dev.strategos.core.testkit.ScenarioReport;
import dev.strategos.core.testkit.Scenarios;
import dev.strategos.core.testkit.Seeds;
import dev.strategos.core.testkit.SimHarness;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Spike S6 (ADR-0009): the canary twice in one JVM gives the same hash at every 100th tick, and those hashes equal the
 * committed golden, which CI checks on Linux against this macOS file (QA-08.3). {@code -Dstrategos.golden.update=true}
 * rewrites the golden (the test JVM runs in {@code core/}); commit that change with its reason (R12).
 */
class DeterminismCanaryTest {
    private static final String GOLDEN = "golden/s6-canary.json";
    private static final int EVERY = 100;

    /** The run in the golden's form: the hash at every 100th tick, from the journal's checkpoints, and the final one. */
    private static JsonObject run(long seed) {
        Scenario canary = Scenarios.canary(seed);
        SimHarness h = SimHarness.forScenario(canary);
        ScenarioReport report = h.play(canary);
        assertThat(report.failure()).isNull();

        Journal journal = h.core().journal();
        long[] ticks = journal.checkpointTicks();
        long[] hashes = journal.checkpointHashes();
        JsonArray checkpoints = new JsonArray();
        for (int i = 0; i < ticks.length; i++) {
            if (ticks[i] % EVERY == 0) {
                JsonArray pair = new JsonArray();
                pair.add(ticks[i]);
                pair.add(Hex64.format(hashes[i]));
                checkpoints.add(pair);
            }
        }
        JsonObject json = new JsonObject();
        json.addProperty("scenario", canary.id());
        json.addProperty("seed", seed);
        json.addProperty("ticks", report.ticks());
        json.addProperty("every", EVERY);
        json.add("checkpoints", checkpoints);
        json.addProperty("finalHash", Hex64.format(report.finalHash()));
        return json;
    }

    @Test
    void theCanaryRepeatsItselfAndMatchesTheGolden() throws IOException {
        long seed = Seeds.first("canary");
        JsonObject first = run(seed);
        JsonObject second = run(seed);

        assertThat(second).as("a second run in the same JVM").isEqualTo(first);
        assertThat(first.getAsJsonArray("checkpoints")).as("ticks 100 to 2,000").hasSize(20);

        if (Boolean.getBoolean("strategos.golden.update")) {
            Path file = Path.of("src/test/resources", GOLDEN);
            Files.createDirectories(file.getParent());
            Files.writeString(
                    file, new GsonBuilder().setPrettyPrinting().create().toJson(first) + "\n", StandardCharsets.UTF_8);
            return;
        }
        try (InputStream in = DeterminismCanaryTest.class.getResourceAsStream("/" + GOLDEN)) {
            assertThat(in).as("the committed golden " + GOLDEN).isNotNull();
            JsonElement committed = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            assertThat(first).as("the canary against " + GOLDEN).isEqualTo(committed);
        }
    }
}
