package dev.strategos.core.sim.journal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.testkit.GoldenJournals;
import dev.strategos.core.testkit.JournalFiles;
import dev.strategos.core.testkit.ReplayRunner;
import dev.strategos.core.testkit.ReplayRunner.ReplayReport;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * CORE-11.3: every golden journal replays to the checkpoint hashes it was recorded with. A change in the core's
 * behaviour fails here until the goldens are recorded again with a written reason.
 */
class GoldenJournalTest {
    private static final String REBASELINE =
            "if the change is intended, run ./gradlew :core:rebaselineJournals -Preason=\"<why the hashes changed>\"";

    static Stream<String> goldens() {
        return GoldenJournals.goldens().keySet().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldens")
    void replaysToItsRecordedHashes(String name) {
        String resource = "/journals/" + name + JournalFiles.EXTENSION;
        JournalExport golden;
        try (InputStream in = GoldenJournalTest.class.getResourceAsStream(resource)) {
            assertThat(in)
                    .withFailMessage("no golden %s; %s", resource, REBASELINE)
                    .isNotNull();
            golden = JournalFormat.decode(in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        ReplayReport report = ReplayRunner.check(golden.journal(), TemplateSet.builtin(), golden.liveHash());

        assertThat(report.ok())
                .withFailMessage("golden %s diverged: %s; %s", name, report.message(), REBASELINE)
                .isTrue();
        assertThat(report.result().finalTick()).isEqualTo(golden.liveTick());
    }
}
