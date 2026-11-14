package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.sim.KernelAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * AI-04.3 in L1: the opening is drawn from the match's seed (stream OPENING, key the faction) by weight at a computer
 * player's first think and kept in its memory; different seeds draw different openings, the same seed the same ones,
 * and an opening changes the script's numbers.
 */
class OpeningTest {
    /** The first-loop script with the skirmish's three openings added (the shipped one has none until T22). */
    private static final String SCRIPT = withOpenings(HeadlessMatch.read("ai/first_loop.json"));

    private static String withOpenings(String script) {
        String openings = """
                ,"openings": [
                  { "name": "rush", "weight": 2, "maxWorkers": 20, "soldierQueue": 3,
                    "attack": { "first": 330, "every": 120, "min": 8, "muster": 40, "announce": 30 } },
                  { "name": "boom", "weight": 2, "maxWorkers": 32, "soldierQueue": 1,
                    "attack": { "first": 600, "every": 240, "min": 18, "muster": 40, "announce": 45 } },
                  { "name": "balanced", "weight": 3 } ] }""";
        return script.substring(0, script.lastIndexOf('}')).stripTrailing() + openings;
    }

    /** The two players' opening names once both have thought, for {@code seed}. */
    private static List<String> drawn(long seed) {
        List<String> names = new ArrayList<>();
        HeadlessMatch.run(
                seed,
                200,
                SCRIPT,
                (h, out) -> {
                    if (h.tick() == 199) {
                        for (int f = 0; f < 2; f++) {
                            names.add(KernelAccess.state(h.core())
                                    .aiSlots()
                                    .get(f)
                                    .openingName());
                        }
                    }
                },
                false);
        return names;
    }

    @Test
    void seedsPickDifferentOpenings() {
        Set<String> seen = new TreeSet<>();
        for (long seed = 1; seed <= 8; seed++) {
            List<String> names = drawn(seed);
            assertThat(names).as("seed " + seed).hasSize(2).doesNotContain("");
            seen.addAll(names);
            assertThat(drawn(seed)).as("seed " + seed + " again").isEqualTo(names);
        }
        assertThat(seen).as("the openings eight seeds draw").containsExactlyInAnyOrder("rush", "boom", "balanced");
    }

    @Test
    void anOpeningChangesTheScriptAndAWeightIsHonoured() {
        StrategyScript script = StrategyScript.parse(SCRIPT);
        assertThat(script.openings())
                .extracting(StrategyScript.Opening::name)
                .containsExactly("rush", "boom", "balanced");
        StrategyScript rush = script.opened(0);
        assertThat(rush.attack().first()).isLessThan(script.attack().first());
        assertThat(rush.soldierQueue()).isGreaterThan(script.soldierQueue());
        StrategyScript boom = script.opened(1);
        assertThat(boom.maxWorkers()).isGreaterThan(script.maxWorkers());
        assertThat(script.opened(2)).as("balanced keeps the script").isEqualTo(script.opened(-1));
        int total = script.totalWeight();
        assertThat(script.pick(0)).isZero();
        assertThat(script.pick(total - 1)).isEqualTo(2);
        int[] hits = new int[3];
        for (int roll = 0; roll < total; roll++) {
            hits[script.pick(roll)]++;
        }
        assertThat(hits).containsExactly(2, 2, 3);
    }

    @Test
    void theShippedFirstLoopHasNoOpenings() {
        assertThat(StrategyScript.parse(HeadlessMatch.read("ai/first_loop.json"))
                        .openings())
                .isEmpty();
    }

    @Test
    void aScriptWithoutOpeningsDrawsNone() {
        String plain = """
                { "maxWorkers": 0, "ratio": {}, "build": [],
                  "attack": { "first": 100000, "every": 100000 }, "sites": [] }""";
        List<String> names = new ArrayList<>();
        HeadlessMatch.run(
                1,
                100,
                plain,
                (h, out) -> {
                    if (h.tick() == 99) {
                        names.add(KernelAccess.state(h.core()).aiSlots().get(0).openingName());
                    }
                },
                false);
        assertThat(names).containsExactly("");
    }
}
