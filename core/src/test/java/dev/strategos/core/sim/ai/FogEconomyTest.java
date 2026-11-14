package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.shared.FactionSlot;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * AI-18 and QA-12 in L1: the economy under real fog (the first skirmish map, nothing explored, no AI vision). A gather
 * on ground the faction never explored is rejected, so the AI sends a worker to explore a resource with no explored
 * node before it gathers there; and the watchdog of a fogged match watches the state, not the commands.
 */
final class FogEconomyTest {
    private static final long MINUTE = 1_200;

    @Test
    void stoneInTheDarkIsExploredThenMined() {
        String normal = Levels.resolve(HeadlessMatch.read("ai/skirmish.json"), "normal");
        int stone = 2; // food, wood, stone, iron
        int[] mostStoneGatherers = {0, 0};
        boolean[] darkAtStart = {false};
        HeadlessMatch.run(
                HeadlessMatch.Ground.firstSkirmish(),
                DifficultyTest.firstSkirmish(),
                1,
                8 * MINUTE,
                List.of(normal, normal),
                (h, out) -> {
                    if (h.tick() == 20) {
                        // the stone node of the west start, 20 blocks from its centre, is unexplored at the start
                        darkAtStart[0] = !h.core().queries().explored(0, 30.5, 118.5);
                    }
                    if (h.tick() % 20 == 0) {
                        for (int f = 0; f < 2; f++) {
                            mostStoneGatherers[f] = Math.max(
                                    mostStoneGatherers[f], h.core().queries().workers(new FactionSlot(f))[stone]);
                        }
                    }
                },
                false,
                HeadlessMatch.FOGGED);
        assertThat(darkAtStart[0]).as("the stone node starts unexplored").isTrue();
        assertThat(Math.min(mostStoneGatherers[0], mostStoneGatherers[1]))
                .as("stone gatherers by minute 8, the fewer faction")
                .isPositive();
    }

    @Test
    void aFoggedMatchThatStopsMovingIsAStall() {
        String idle = """
                { "maxWorkers": 0, "ratio": {}, "build": [],
                  "attack": { "first": 100000, "every": 100000 }, "sites": [] }""";
        HeadlessMatch.Summary s = HeadlessMatch.run(
                        HeadlessMatch.Ground.firstSkirmish(),
                        DifficultyTest.firstSkirmish(),
                        1,
                        10 * MINUTE,
                        List.of(idle, idle),
                        (h, out) -> {},
                        true,
                        HeadlessMatch.FOGGED)
                .summary();
        assertThat(s.failure()).contains("no state change");
    }

    /**
     * QA-12: boom against balanced, seed 9, first skirmish map: a ram trained inside the walled pocket of its arsenal
     * was ordered to burn a building 280 blocks off; its path ended on its own cell, it arrived there and asked again,
     * every few ticks, WALKING without progress from tick 24,964 on. The order must fail and the match go on.
     */
    @Test
    void aRamWalledInByItsArsenalFailsItsOrder() {
        String skirmish = HeadlessMatch.read("ai/skirmish.json");
        String boom = Levels.resolve(skirmish, "normal", "boom");
        String balanced = Levels.resolve(skirmish, "normal", "balanced");
        HeadlessMatch.Summary s = HeadlessMatch.run(
                        HeadlessMatch.Ground.firstSkirmish(),
                        DifficultyTest.firstSkirmish(),
                        9,
                        26_000,
                        List.of(balanced, boom),
                        (h, out) -> {},
                        false,
                        HeadlessMatch.FOGGED)
                .summary();
        assertThat(s.failure()).isEmpty();
    }
}
