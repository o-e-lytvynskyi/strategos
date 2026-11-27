package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.content.Scenario;
import dev.strategos.core.content.TechDef;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.sim.TechResearched;
import dev.strategos.core.sim.UnitView;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * AI-09 in L1 (M10 §2.19, T25b): City as a capability. Slot 0 plays the shipped skirmish script with the {@code depth}
 * block of the test resource {@code ai/skirmish-depth.json} merged in (the shipped script keeps it off until M11), at
 * Normal; slot 1 plays the shipped script at Normal as well, with its attacks off ({@code attack.first} beyond the
 * match's limit, the balanced opening so no opening overrides it): it builds, gathers and defends but never attacks.
 * First skirmish map, fog on, no victory rule, so the match plays to {@link #LIMIT} in {@link #SEEDS} seeded matches. Slot
 * 0 reaches City, researches technologies of the second and third tier, trains a healer, marches in a formation and
 * charges. No stock is granted.
 */
final class AiCityTest {
    static final long[] SEED_LIST = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
    static final long SEEDS = SEED_LIST.length;

    /** The bar (AI-09): seeds, of {@link #SEEDS}, in which slot 0 reaches City. */
    static final int MIN_SEEDS_WITH_CITY = 11;

    private static final long MINUTE = 1_200;
    private static final long LIMIT = 45 * MINUTE;
    private static final String CITY = "strategos:phase_city";
    private static final String TOWN = "strategos:phase_town";
    private static final String HEALER = "strategos:unit/healer";

    /** What slot 0 did in one match. */
    record Run(long town, long city, int tier2, int tier3, int healers, boolean formation, boolean charge) {}

    /** The shipped skirmish script with the test resource's {@code depth} block merged in. */
    static String withDepth(String skirmish) {
        try (InputStream in = AiCityTest.class.getResourceAsStream("/ai/skirmish-depth.json")) {
            JsonObject depth = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            JsonObject script = JsonParser.parseString(skirmish).getAsJsonObject();
            depth.entrySet().forEach(e -> script.add(e.getKey(), e.getValue()));
            return script.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The opponent's script: the shipped one at Normal, the balanced opening; no wave ever leaves: its first attack is
     * out of the match, its size out of reach, and the decisive wave ({@code battle.finish}) off.
     */
    static String peaceful(String skirmish) {
        JsonObject script = JsonParser.parseString(Levels.resolve(skirmish, "normal", "balanced"))
                .getAsJsonObject();
        script.getAsJsonObject("attack").addProperty("first", 10 * LIMIT);
        script.getAsJsonObject("attack").addProperty("min", 1_000_000);
        script.getAsJsonObject("battle").addProperty("finish", false); // no decisive wave on seeing a weaker army
        return script.toString();
    }

    /** The tier of {@code tech} in the tree: how many techs it supersedes in a chain, itself included. */
    static int tier(TemplateSet set, TechDef tech) {
        int tier = 1;
        TechId below = tech.supersedes();
        while (below != null) {
            tier++;
            TechDef t = set.tech(below);
            below = t == null ? null : t.supersedes();
        }
        return tier;
    }

    /** The match of {@code seed}: what slot 0 did. */
    static Run play(long seed) {
        String skirmish = HeadlessMatch.read("ai/skirmish.json");
        TemplateSet set = TemplateSet.builtin();
        Scenario first = DifficultyTest.firstSkirmish();
        Scenario noVictory = new Scenario(first.map(), "none", first.slots());
        long[] at = {-1, -1}; // town, city
        int[] tier = new int[2]; // tier 2, tier 3
        int[] healers = new int[1];
        boolean[] seen = new boolean[2]; // formation, charge
        HeadlessMatch.Result r = HeadlessMatch.run(
                HeadlessMatch.Ground.firstSkirmish(),
                noVictory,
                seed,
                LIMIT,
                List.of(Levels.resolve(withDepth(skirmish), "normal"), peaceful(skirmish)),
                (h, out) -> {
                    for (var e : out.events()) {
                        if (e instanceof TechResearched t && t.faction().value() == 0) {
                            TechDef def = set.tech(t.tech());
                            if (t.tech().value().equals(CITY)) {
                                at[1] = out.tick();
                            } else if (t.tech().value().equals(TOWN)) {
                                at[0] = out.tick();
                            } else if (def != null && tier(set, def) >= 2 && tier(set, def) <= 3) {
                                tier[tier(set, def) - 2]++;
                            }
                        }
                    }
                    if (out.tick() % 20 != 0) {
                        return;
                    }
                    int now = 0;
                    for (int handle : h.core().queries().unitHandles()) {
                        UnitView u = h.core().queries().unit(handle);
                        if (u == null || u.faction().value() != 0) {
                            continue;
                        }
                        now += u.template().value().equals(HEALER) ? 1 : 0;
                        seen[0] |= h.core().queries().formationOf(handle) != 0;
                        seen[1] |= h.core().queries().abilityCooldown(handle, "charge") > 0;
                    }
                    healers[0] = Math.max(healers[0], now);
                },
                false,
                HeadlessMatch.FOGGED);
        assertThat(r.summary().failure()).as("seed " + seed).isEmpty();
        return new Run(at[0], at[1], tier[0], tier[1], healers[0], seen[0], seen[1]);
    }

    @Test
    @Tag("slow")
    void theAiReachesCityAndPlaysTheNewContent() {
        List<Run> runs = LongStream.of(SEED_LIST).mapToObj(AiCityTest::play).toList();
        int city = 0;
        int tier2 = 0;
        int tier3 = 0;
        int healers = 0;
        int formation = 0;
        int charge = 0;
        List<String> lines = new ArrayList<>();
        for (int k = 0; k < runs.size(); k++) {
            Run x = runs.get(k);
            lines.add(String.format(
                    "seed %d: town %.1f, city %.1f min; tier-2 %d, tier-3 %d; healers %d; formation %s; charge %s",
                    SEED_LIST[k],
                    x.town() / (double) MINUTE,
                    x.city() / (double) MINUTE,
                    x.tier2(),
                    x.tier3(),
                    x.healers(),
                    x.formation(),
                    x.charge()));
            city += x.city() >= 0 ? 1 : 0;
            tier2 += x.tier2() > 0 ? 1 : 0;
            tier3 += x.tier3() > 0 ? 1 : 0;
            healers += x.healers() > 0 ? 1 : 0;
            formation += x.formation() ? 1 : 0;
            charge += x.charge() ? 1 : 0;
        }
        lines.forEach(System.out::println);
        System.out.printf(
                "AI-09: City in %d of %d seeds; tier 2 in %d, tier 3 in %d, healers in %d, formation in %d, charge in %d%n",
                city, SEEDS, tier2, tier3, healers, formation, charge);
        assertThat(city)
                .as("seeds, of %d, in which the computer player reached City: %s", SEEDS, lines)
                .isGreaterThanOrEqualTo(MIN_SEEDS_WITH_CITY);
        assertThat(tier2).as("seeds with a tier-2 technology").isPositive();
        assertThat(tier3).as("seeds with a tier-3 technology").isPositive();
        assertThat(healers).as("seeds with a healer").isPositive();
        assertThat(formation).as("seeds with a formation march").isPositive();
        assertThat(charge).as("seeds with a charge").isPositive();
    }
}
