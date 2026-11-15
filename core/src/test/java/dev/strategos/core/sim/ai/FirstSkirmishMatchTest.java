package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.Scenario;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * MAP-02 (spec T12.2) and risk R5: one headless match on the first skirmish map, both slots the first-loop computer
 * under fog, timed, so QA-12's thousand matches can be planned. Its winner and length are recorded, not judged:
 * AI-18.3 and QA-12 hold fogged matches to 45 minutes in T23.
 */
final class FirstSkirmishMatchTest {
    private static final long FORTY_FIVE_MINUTES = 45L * 60 * 20;

    @Test
    @Tag("slow")
    void oneHeadlessMatchOnTheFirstSkirmishMap() {
        Scenario scenario = Scenario.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString(HeadlessMatch.read("scenarios/first_skirmish.json")))
                .getOrThrow();
        long started = System.nanoTime();
        HeadlessMatch.Result run = HeadlessMatch.run(
                HeadlessMatch.Ground.firstSkirmish(),
                scenario,
                1,
                FORTY_FIVE_MINUTES,
                HeadlessMatch.read("ai/first_loop.json"),
                (h, out) -> {},
                false);
        double seconds = (System.nanoTime() - started) / 1e9;
        HeadlessMatch.Summary s = run.summary();
        System.out.printf(
                "MAP-02: one headless match on first_skirmish, seed 1: winner %d after %d ticks (%.1f game minutes), "
                        + "first contact at %d, %d waves; %.1f s of wall time%n",
                s.winner(),
                s.ticks(),
                s.ticks() / 1200.0,
                s.firstContact(),
                s.waves().size(),
                seconds);
        assertThat(s.failure()).as("the run failed").isEmpty();
        assertThat(s.ticks()).as("the match ran").isPositive();
    }
}
