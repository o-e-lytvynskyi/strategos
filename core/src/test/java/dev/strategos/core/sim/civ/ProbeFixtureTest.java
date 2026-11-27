package dev.strategos.core.sim.civ;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * CIV-05: the probe civilisation lives in test fixtures only. No file under {@code core/src/main} or {@code
 * mod/src/main}, resources and built-in templates included, names {@code strategos:probe}; and the GameTest pack's data
 * (read by the L1 tests too) does.
 */
class ProbeFixtureTest {
    private static final String PROBE = "strategos:probe";
    private static final String PROBE_FILE = "civs/probe";

    private static List<Path> sources(String root) throws IOException {
        Path dir = Path.of(root);
        assertThat(dir).as("a source root").isDirectory();
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile).toList();
        }
    }

    @Test
    void noMainSourceNamesTheProbe() throws IOException {
        for (String root : new String[] {"src/main", "../mod/src/main"}) {
            for (Path file : sources(root)) {
                assertThat(file.toString()).as("a probe file in main").doesNotContain(PROBE_FILE);
                if (file.toString().matches(".*\\.(java|json|lang|mcmeta|toml|txt|md|properties|snbt)$")) {
                    assertThat(Files.readString(file)).as(file.toString()).doesNotContain(PROBE);
                }
            }
        }
    }

    @Test
    void theFixtureHoldsTheProbe() throws IOException {
        Path civ = Path.of("../mod/src/gametest/resources/probe/civs/probe.json");
        assertThat(civ).isRegularFile();
        assertThat(sources("src/testFixtures").stream()
                        .anyMatch(f -> f.toString().endsWith("ProbeCiv.java")))
                .isTrue();
    }
}
