package dev.strategos.core.sim.journal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.testkit.GoldenJournals;
import dev.strategos.core.testkit.Scenarios;
import dev.strategos.core.testkit.Seeds;
import java.util.List;
import org.junit.jupiter.api.Test;

/** CORE-11.2: a journal is cut into segments every 1,200 ticks, and each replays from its own snapshot. */
class JournalSegmentTest {

    @Test
    void cutsASegmentEveryTwelveHundredTicks() {
        JournalExport canary = GoldenJournals.record(Scenarios.canary(Seeds.first("canary")));

        List<Journal.Segment> segments = canary.journal().segments();

        assertThat(segments).extracting(Journal.Segment::startTick).containsExactly(0L, 1_200L);
        assertThat(segments.get(0).lastTick()).isEqualTo(1_200L);
        assertThat(segments.get(1).lastTick()).isEqualTo(canary.liveTick());
        assertThat(segments.get(0).checkpointTicks()).endsWith(1_200L);
    }

    @Test
    void eachSegmentReplaysFromItsOwnSnapshot() {
        JournalExport canary = JournalFormat.decode(
                JournalFormat.encode(GoldenJournals.record(Scenarios.canary(Seeds.first("canary")))));
        Journal journal = canary.journal();

        for (int i = 0; i < journal.segments().size(); i++) {
            ReplayResult replay = ReplayDriver.replay(journal.segment(i), TemplateSet.builtin());
            assertThat(replay.matches()).as("segment %d", i).isTrue();
            assertThat(replay.finalTick()).isEqualTo(journal.segments().get(i).lastTick());
        }
        ReplayResult last = ReplayDriver.replay(journal.segment(1), TemplateSet.builtin());
        assertThat(last.finalHash()).isEqualTo(canary.liveHash());
    }
}
