package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.TemplateId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CMB-07 on the economy bot (AI-02): a civic centre shoots, so a wave of a handful of spearmen only feeds it. The
 * script holds each wave back until its minimum stands. Sent a few at a time, the waves died to the arrows while the
 * wood-starved economy could not replace the soldiers: a silent bot, and 30-minute matches without a winner.
 */
class DefendedCentreWaveTest {
    static final TemplateId SOLDIER = new TemplateId("strategos:unit/infantry");

    @Test
    void everyWaveLeavesWithAtLeastTheScriptsMinimum() {
        String json = HeadlessMatch.read("ai/economy.json");
        List<Integer> soldiers = new ArrayList<>(); // faction 0's spearmen after each tick, by tick
        soldiers.add(0);
        soldiers.add(0);
        HeadlessMatch.Summary m = HeadlessMatch.run(
                        4,
                        18_000,
                        json,
                        (h, out) -> soldiers.add((int) java.util.Arrays.stream(
                                        h.core().queries().unitHandlesOf(new FactionSlot(0)))
                                .filter(u ->
                                        h.core().queries().unit(u).template().equals(SOLDIER))
                                .count()),
                        false)
                .summary();
        assertThat(m.leftAt()).as("a wave left by minute 15").isNotEmpty();
        for (long left : m.leftAt()) {
            assertThat(soldiers.get((int) left + 1))
                    .as("the soldiers standing as the wave left, tick %d", left)
                    .isGreaterThanOrEqualTo(14);
        }
    }

    @Test
    void theScriptHoldsAWaveForADefendedCentre() {
        StrategyScript script = StrategyScript.parse(HeadlessMatch.read("ai/economy.json"));
        assertThat(script.attack().min())
                .as("a wave strong enough for a defended centre")
                .isGreaterThanOrEqualTo(14);
    }
}
