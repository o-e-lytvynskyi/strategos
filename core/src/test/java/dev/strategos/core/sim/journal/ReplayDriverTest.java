package dev.strategos.core.sim.journal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.InputEvent;
import dev.strategos.core.sim.TickInputs;
import dev.strategos.core.testkit.SimHarness;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReplayDriverTest {

    @Test
    void aTwoThousandTickRandomRunReplaysToTheSameHashAndCheckpoints() {
        SimHarness h = RandomRun.run(21, 2_000);
        Journal journal = h.core().journal();
        assertThat(journal.checkpointTicks()).hasSize(100);
        assertThat(h.results()).as("the run refused some commands").anyMatch(r -> r.firstReason() != null);

        ReplayResult replay = ReplayDriver.replay(journal, TemplateSet.builtin());

        assertThat(replay.matches()).isTrue();
        assertThat(replay.firstDivergentCheckpoint()).isEqualTo(-1);
        assertThat(replay.finalTick()).isEqualTo(h.tick());
        assertThat(replay.finalHash()).isEqualTo(h.stateHash());
    }

    @Test
    void removingOneRecordedCommandReportsTheFirstDivergentCheckpoint() {
        SimHarness h = RandomRun.run(22, 400);
        Journal journal = h.core().journal();
        // The first accepted Move after tick 100: removing it must change the state from its tick on.
        CommandResult accepted = h.results().stream()
                .filter(r -> r.kind().equals("strategos:move") && r.accepted() > 0)
                .filter(r -> tickOf(journal, r) > 100)
                .findFirst()
                .orElseThrow();
        long removedAt = tickOf(journal, accepted);

        List<TickInputs> ticks = new ArrayList<>();
        for (TickInputs tick : journal.nonEmptyTicks()) {
            List<InputEvent> kept = tick.events().stream()
                    .filter(e -> !(e instanceof CommandEnvelope c
                            && c.clientSeq() == accepted.clientSeq()
                            && c.issuer().equals(accepted.issuer())))
                    .toList();
            if (!kept.isEmpty()) {
                ticks.add(new TickInputs(tick.tick(), kept));
            }
        }
        Journal broken = new Journal(
                journal.checkpointEvery(),
                List.of(new Journal.Segment(
                        journal.start(),
                        journal.lastTick(),
                        ticks,
                        journal.checkpointTicks(),
                        journal.checkpointHashes())));

        ReplayResult replay = ReplayDriver.replay(broken, TemplateSet.builtin());

        long firstCheckpointAfter = ((removedAt + 19) / 20) * 20;
        assertThat(replay.matches()).isFalse();
        assertThat(replay.firstDivergentCheckpoint()).isEqualTo(firstCheckpointAfter);
        assertThat(replay.actualHash()).isNotEqualTo(replay.expectedHash());
    }

    private static long tickOf(Journal journal, CommandResult result) {
        for (TickInputs tick : journal.nonEmptyTicks()) {
            for (InputEvent e : tick.events()) {
                if (e instanceof CommandEnvelope c
                        && c.clientSeq() == result.clientSeq()
                        && c.issuer().equals(result.issuer())
                        && c.command() instanceof Move) {
                    return tick.tick();
                }
            }
        }
        throw new AssertionError("no recorded command for " + result);
    }
}
