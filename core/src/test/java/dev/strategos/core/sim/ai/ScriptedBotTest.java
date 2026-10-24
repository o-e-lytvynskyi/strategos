package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * AI-17.1, .2 and .4 in L1: in a seeded match on the proving ground the first-loop bot finishes its build order before
 * its first wave, announces that wave at 7:30 and sends it at 8:00 (the timing target), each within a think's 20 ticks, announces every wave at least
 * 30 s before it leaves, and no think spends past its budget.
 */
class ScriptedBotTest {
    static HeadlessMatch.Summary match;

    @BeforeAll
    static void play() {
        match = HeadlessMatch.play(1, HeadlessMatch.LIMIT);
    }

    @Test
    void completesItsBuildOrderAndLaunchesTheFirstWaveOnTime() {
        assertThat(match.failure()).isEmpty();
        long announced = match.announcedAt().get(0);
        assertThat(match.buildOrderDoneAt().get(0))
                .as("the build order done before the first wave")
                .isPositive()
                .isLessThan(announced);
        assertThat(announced)
                .as("announced at 7:30, in the tick after its first think since")
                .isBetween(20L * 450 + 1, 20L * 450 + 20);
        assertThat(match.leavesAt().get(0)).as("to leave 30 s later, 8:00").isEqualTo(announced + 600);
        assertThat(match.leftAt().get(0) + 1)
                .as("its attack order applied then")
                .isEqualTo(announced + 600);
    }

    @Test
    void everyWaveIsAnnouncedThirtySecondsAhead() {
        assertThat(match.leftAt()).hasSizeGreaterThanOrEqualTo(2);
        for (int k = 0; k < match.leftAt().size(); k++) {
            long applied = match.leftAt().get(k) + 1; // the order applies in the next tick
            assertThat(applied - match.announcedAt().get(k)).as("wave %d", k).isGreaterThanOrEqualTo(600);
            assertThat(applied)
                    .as("wave %d leaves when it said", k)
                    .isEqualTo(match.leavesAt().get(k));
        }
    }

    @Test
    void eachThinkStaysWithinTheBudget() {
        assertThat(match.maxSpent()).isPositive().isLessThan(AiBudget.CAP);
    }
}
