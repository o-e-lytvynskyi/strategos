package dev.strategos.core.testkit;

import dev.strategos.core.sim.journal.JournalExport;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The golden journals under {@code core/src/test/resources/journals/} (CORE-11.3): each is a scenario run whose replay
 * must reach the same checkpoint hashes in every later build. {@code ./gradlew :core:rebaselineJournals
 * -Preason="…"} runs {@link #main} to record them again and appends the reason to {@code REBASELINE.md}.
 */
public final class GoldenJournals {
    public static final String REBASELINE_LOG = "REBASELINE.md";

    private GoldenJournals() {}

    /**
     * File name (without the extension) → the scenario that records it; the canary spans two journal segments, and the
     * synthetic crossing carries terrain pages and path requests.
     */
    public static Map<String, Scenario> goldens() {
        Map<String, Scenario> goldens = new LinkedHashMap<>();
        goldens.put("walking_skeleton", Scenarios.walkingSkeleton(Seeds.first("skeleton-20")));
        goldens.put("canary", Scenarios.canary(Seeds.first("canary")));
        goldens.put("crossing_synthetic", Scenarios.crossingSynthetic());
        return goldens;
    }

    /** Plays the scenario on a fresh harness and returns its journal with the live tick and hash. */
    public static JournalExport record(Scenario scenario) {
        SimHarness h = SimHarness.forScenario(scenario);
        ScenarioReport report = h.play(scenario);
        if (!report.passed()) {
            throw new IllegalStateException("golden scenario " + scenario.id() + " failed: " + report.failure());
        }
        return new JournalExport(h.core().journal(), h.tick(), h.stateHash());
    }

    /** {@code args}: the journals directory and the reason; records every golden and logs the reason. */
    public static void main(String[] args) {
        if (args.length != 2 || args[1].isBlank()) {
            throw new IllegalArgumentException("usage: GoldenJournals <dir> <reason>; a re-baseline needs a reason");
        }
        Path dir = Path.of(args[0]);
        for (Map.Entry<String, Scenario> golden : goldens().entrySet()) {
            JournalFiles.write(dir.resolve(golden.getKey() + JournalFiles.EXTENSION), record(golden.getValue()));
        }
        String line = "- " + LocalDate.now(ZoneOffset.UTC) + ": "
                + String.join(", ", goldens().keySet()) + " — " + args[1].strip() + System.lineSeparator();
        try {
            Path log = dir.resolve(REBASELINE_LOG);
            if (!Files.exists(log)) {
                Files.writeString(
                        log,
                        "# Golden journal re-baselines" + System.lineSeparator() + System.lineSeparator(),
                        StandardCharsets.UTF_8);
            }
            Files.writeString(log, line, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot append to " + REBASELINE_LOG, e);
        }
    }
}
