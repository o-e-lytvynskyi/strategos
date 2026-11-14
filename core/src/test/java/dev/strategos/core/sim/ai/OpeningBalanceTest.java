package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** QA-12's judge: the thresholds of {@code :core:openingBalance} against synthetic rows, each criterion on its own. */
final class OpeningBalanceTest {
    private static final long MINUTE = 1_200;

    /** 200 rows of {@code p}: A wins {@code aWins} of them, the start-0 side {@code zeroWins}, every match {@code minutes}. */
    private static List<OpeningBalance.Row> rows(OpeningBalance.Pairing p, int aWins, long minutes) {
        List<OpeningBalance.Row> out = new ArrayList<>();
        for (long seed = 1; seed <= 200; seed++) {
            int aSlot = seed % 2 == 0 ? 0 : 1;
            boolean aWon = seed <= aWins;
            int winner = aWon ? aSlot : 1 - aSlot;
            out.add(new OpeningBalance.Row(
                    p.name(),
                    seed,
                    aSlot,
                    winner,
                    aWon,
                    minutes * MINUTE,
                    5_000,
                    List.of("", ""),
                    List.of(-1L, -1L),
                    List.of(0L, 0L),
                    List.of(0L, 0L),
                    List.of(0, 0),
                    List.of(0L, 0L),
                    List.of(1, 1),
                    List.of(0, 0),
                    List.of(),
                    1,
                    ""));
        }
        return out;
    }

    private static List<String> judge(OpeningBalance.Pairing p, int aWins, long minutes) {
        return OpeningBalance.judge(List.of(p), rows(p, aWins, minutes));
    }

    @Test
    void theFivePairingsAreTheSpecs() {
        assertThat(OpeningBalance.pairings())
                .extracting(OpeningBalance.Pairing::name)
                .containsExactly("rush-boom", "rush-balanced", "boom-balanced", "hard-normal", "normal-easy");
    }

    @Test
    void anOpeningMustWinSixtyToOneHundredForty() {
        OpeningBalance.Pairing p = OpeningBalance.Pairing.openings("rush", "boom");
        assertThat(judge(p, 100, 27)).isEmpty();
        assertThat(judge(p, 60, 27)).noneMatch(m -> m.contains("QA-12.2"));
        assertThat(judge(p, 59, 27)).anyMatch(m -> m.contains("QA-12.2"));
        assertThat(judge(p, 140, 27)).noneMatch(m -> m.contains("QA-12.2"));
        assertThat(judge(p, 141, 27)).anyMatch(m -> m.contains("QA-12.2"));
    }

    @Test
    void aLevelMustWinAtLeastOneHundredTwentyFour() {
        OpeningBalance.Pairing p = OpeningBalance.Pairing.levels("hard", "normal");
        assertThat(judge(p, 124, 27)).isEmpty();
        assertThat(judge(p, 123, 27)).anyMatch(m -> m.contains("AI-05.2"));
    }

    @Test
    void theMedianIsTwentyToThirtyFiveMinutes() {
        OpeningBalance.Pairing p = OpeningBalance.Pairing.openings("rush", "boom");
        assertThat(judge(p, 100, 20)).isEmpty();
        assertThat(judge(p, 100, 35)).isEmpty();
        assertThat(judge(p, 100, 19)).anyMatch(m -> m.contains("QA-12.1"));
        assertThat(judge(p, 100, 36)).anyMatch(m -> m.contains("QA-12.1"));
    }

    @Test
    void aMatchWithoutConquestOrWithAFailureIsReported() {
        OpeningBalance.Pairing p = OpeningBalance.Pairing.openings("rush", "boom");
        List<OpeningBalance.Row> rows = new ArrayList<>(rows(p, 100, 27));
        OpeningBalance.Row r = rows.get(0);
        rows.set(
                0,
                new OpeningBalance.Row(
                        r.pairing(),
                        r.seed(),
                        r.aSlot(),
                        -2,
                        false,
                        r.ticks(),
                        r.firstContact(),
                        r.openings(),
                        r.townAt(),
                        r.commands(),
                        r.waves(),
                        r.unitsLost(),
                        r.idleWorkerSeconds(),
                        r.centres(),
                        r.soldiers(),
                        r.mop(),
                        1,
                        "faction 0 stalled: no command since tick 9"));
        assertThat(OpeningBalance.judge(List.of(p), rows)).anyMatch(m -> m.contains("stalled"));
    }

    @Test
    void aTrialPlaysAndWritesItsSummary(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        List<OpeningBalance.Row> rows =
                OpeningBalance.playAll(List.of(OpeningBalance.Pairing.openings("rush", "boom")), 2, 2, dir);
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(OpeningBalance.Row::aSlot).containsExactly(1, 0);
        assertThat(rows.get(0).openings()).as("the pinned openings, by slot").containsExactly("boom", "rush");
        assertThat(OpeningBalance.markdown(List.of(OpeningBalance.Pairing.openings("rush", "boom")), rows))
                .contains("rush-boom", "## Openings");
    }
}
