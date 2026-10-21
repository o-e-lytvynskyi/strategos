package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.sim.journal.JournalExport;
import dev.strategos.core.testkit.JournalFiles;
import dev.strategos.core.testkit.ReplayRunner;
import dev.strategos.core.testkit.ReplayRunner.ReplayReport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * ORD-01.4, S6 item 3: every journal a GameTest exported replays in plain JUnit to the hash the live match had. Run by
 * {@code :core:replayJournals}, which first runs the GameTests and sets {@code strategos.journals.dir}.
 */
@Tag("replay")
class GameTestJournalReplayTest {
    static Stream<Arguments> journals() throws IOException {
        String dir = System.getProperty("strategos.journals.dir");
        if (dir == null) {
            return Stream.empty();
        }
        List<Path> files;
        try (Stream<Path> listed = Files.list(Path.of(dir))) {
            files = listed.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }
        return files.stream().map(p -> Arguments.of(Named.of(p.getFileName().toString(), p)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("journals")
    void replaysToTheLiveHash(Path file) {
        JournalExport export = JournalFiles.read(file);

        ReplayReport report = ReplayRunner.check(export.journal(), TemplateSet.builtin(), export.liveHash());

        assertThat(report.ok())
                .withFailMessage("%s: %s", file.getFileName(), report.message())
                .isTrue();
        assertThat(report.result().finalTick())
                .withFailMessage(
                        "%s: the replay ended at tick %d, the live match at %d",
                        file.getFileName(), report.result().finalTick(), export.liveTick())
                .isEqualTo(export.liveTick());
    }
}
