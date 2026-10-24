package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonParser;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.sim.journal.JournalExport;
import dev.strategos.core.sim.journal.ReplayDriver;
import dev.strategos.core.sim.journal.ReplayResult;
import dev.strategos.core.testkit.JournalFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * QA-11.1–2 in L1: ten seeded bot matches run from one call and write a summary with their telemetry; an exception, a
 * stall or a match unfinished at the limit fails its run, and a failing seed's journal replays to its hash.
 */
class HeadlessMatchesTest {
    @Test
    void tenSeededMatchesWriteASummary(@TempDir Path dir) throws Exception {
        List<HeadlessMatch.Summary> all = HeadlessMatches.run(HeadlessMatches.seeds("1..10"), HeadlessMatch.LIMIT, dir);
        assertThat(all).hasSize(10).allMatch(s -> !s.failed() && s.winner() >= 0);
        var json = JsonParser.parseString(Files.readString(dir.resolve("summary.json")))
                .getAsJsonArray();
        assertThat(json).hasSize(10);
        var first = json.get(0).getAsJsonObject();
        for (String field : List.of(
                "winner", "ticks", "firstContact", "army", "idleWorkerSeconds", "banked", "unitsLost", "stuck")) {
            assertThat(first.has(field)).as(field).isTrue();
        }
        assertThat(HeadlessMatches.seeds("1..3,7")).containsExactly(1L, 2L, 3L, 7L);
    }

    @Test
    void anExceptionAStallOrNoWinnerFails() {
        String script = HeadlessMatch.read("ai/first_loop.json");
        assertThat(HeadlessMatch.run(1, 3_000, script, t -> {}).summary().failure())
                .isEqualTo("no winner by tick 3000");
        assertThat(HeadlessMatch.run(1, 3_000, script, t -> {
                            if (t == 500) {
                                throw new IllegalStateException("boom");
                            }
                        })
                        .summary()
                        .failure())
                .startsWith("exception at tick 500");
        String idle = """
                { "maxWorkers": 0, "ratio": {}, "build": [],
                  "attack": { "first": 100000, "every": 100000 }, "sites": [] }""";
        assertThat(HeadlessMatch.run(1, 5_000, idle, t -> {}).summary().failure())
                .as("a player that never commands has stalled")
                .contains("stalled");
    }

    @Test
    void aFailingSeedsJournalReplays(@TempDir Path dir) throws Exception {
        HeadlessMatch.Result r = HeadlessMatch.run(4, 2_000, HeadlessMatch.read("ai/first_loop.json"), t -> {});
        assertThat(r.summary().failed()).isTrue();
        Path file = dir.resolve("seed-4" + JournalFiles.EXTENSION);
        JournalFiles.write(file, r.journal());
        JournalExport back = JournalFiles.read(file);
        ReplayResult replay = ReplayDriver.replay(back.journal(), TemplateSet.builtin());
        assertThat(replay.matches()).isTrue();
        assertThat(replay.finalHash()).isEqualTo(r.summary().journalHash());
    }
}
