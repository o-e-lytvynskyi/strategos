package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.Scenario;
import dev.strategos.core.sim.TechResearched;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * AI-05 in L1 (M6 §2.20): Normal reaches Town at {@code targets.json}'s 12:00 within two minutes, in ten seeded
 * matches on the first skirmish map; no level takes a bonus and every handicap is listed in setup.
 */
final class DifficultyTest {
    private static final long MINUTE = 1_200;
    private static final String PHASE_TOWN = "strategos:phase_town";

    static Scenario firstSkirmish() {
        return Scenario.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString(HeadlessMatch.read("scenarios/first_skirmish.json")))
                .getOrThrow();
    }

    /** The tick each faction researched Town at in the match of {@code seed}, -1 for never within {@code limit}. */
    static long[] townAt(long seed, List<String> scripts, long limit) {
        long[] at = {-1, -1};
        HeadlessMatch.Result r = HeadlessMatch.run(
                HeadlessMatch.Ground.firstSkirmish(),
                firstSkirmish(),
                seed,
                limit,
                scripts,
                (h, out) -> {
                    for (var e : out.events()) {
                        if (e instanceof TechResearched t && t.tech().value().equals(PHASE_TOWN)) {
                            at[t.faction().value()] = out.tick();
                        }
                    }
                },
                false);
        assertThat(r.summary().failure()).as("seed " + seed).isEmpty();
        return at;
    }

    @Test
    @Tag("slow")
    void normalReachesTownOnTime() {
        String normal = Levels.resolve(HeadlessMatch.read("ai/skirmish.json"), "normal");
        List<String> minutes = new ArrayList<>();
        List<Long> late = new ArrayList<>();
        for (long seed = 1; seed <= 10; seed++) {
            long[] at = townAt(seed, List.of(normal, normal), 16 * MINUTE);
            for (int f = 0; f < 2; f++) {
                minutes.add(String.format("%d/%d: %.2f", seed, f, at[f] < 0 ? -1.0 : at[f] / (double) MINUTE));
                if (at[f] < 10 * MINUTE || at[f] > 14 * MINUTE) {
                    late.add(seed * 10 + f);
                }
            }
        }
        System.out.println("AI-05 Normal's Town times (seed/faction: minutes): " + minutes);
        assertThat(late)
                .as("seed*10+faction outside 12:00 +- 2 min; times %s", minutes)
                .isEmpty();
    }

    /** AI-05.3: no level is better than the plain script for gathering or training, and setup lists each handicap. */
    @Test
    void noBonusAndEveryHandicapListed() throws IOException {
        String skirmish = HeadlessMatch.read("ai/skirmish.json");
        JsonObject lang = JsonParser.parseString(
                        Files.readString(Path.of("../mod/src/main/resources/assets/strategos/lang/en_us.json")))
                .getAsJsonObject();
        for (String level : Levels.names()) {
            Levels.Knobs k = Levels.knobs(skirmish, level);
            StrategyScript.Handicap h = k.handicap();
            assertThat(h.gather())
                    .as(level + " gathers at most the template's rate")
                    .isLessThanOrEqualTo(1);
            assertThat(h.train())
                    .as(level + " trains no quicker than the template")
                    .isGreaterThanOrEqualTo(1);
            String listed =
                    lang.get("strategos.skirmish.level." + level + ".handicaps").getAsString();
            assertThat(listed).as(level + " is listed in setup").isNotBlank();
            if (h.gather() != 1) {
                assertThat(listed).as(level + " lists its gathering").contains(Double.toString(h.gather()));
            }
            if (h.train() != 1) {
                assertThat(listed).as(level + " lists its training time").contains(Double.toString(h.train()));
            }
            assertThat(StrategyScript.parse(Levels.resolve(skirmish, level))
                            .level()
                            .handicap())
                    .as(level + "'s resolved handicap is the listed one")
                    .isEqualTo(h);
        }
        assertThat(Levels.knobs(skirmish, "normal").handicap().any())
                .as("Normal's handicap")
                .isFalse();
        assertThat(Levels.knobs(skirmish, "hard").handicap().any())
                .as("Hard takes no bonus, nor a handicap")
                .isFalse();
        assertThat(Levels.knobs(skirmish, "easy").handicap().any())
                .as("Easy's handicap is open")
                .isTrue();
    }
}
