package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.content.RawCiv;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.shared.CivId;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.sim.civ.AiHint;
import dev.strategos.core.sim.civ.CivRules;
import dev.strategos.core.sim.civ.ConstructionRule;
import dev.strategos.core.sim.civ.DeliveryRule;
import dev.strategos.core.sim.civ.PopulationRule;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CIV-05, AI-09: the AI plays the rules it is given. Each variant alone (a fixed population, direct delivery,
 * self-building foundations) is played by faction 0 of a seeded headless match against the default AI: its economy and
 * army keep growing, it never stalls, and it does nothing its hint excludes: no house, no dropsite, no builder sent. The
 * default civilisation keeps every hint, so its decisions (the existing AI tests and pinned hashes) are unchanged.
 */
class AiHintsTest {
    private static final CivId VARIANT = new CivId("t:variant");
    private static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    private static final TemplateId STOREHOUSE = new TemplateId("strategos:building/storehouse");
    private static final long MINUTE = 1_200;
    private static final long MINUTES_12 = 12 * MINUTE;
    private static final long[] SEEDS = {1, 2, 3};

    /** The built-in templates with civilisation {@link #VARIANT}: the default's, playing {@code rules}. */
    private static TemplateSet variant(String rules) {
        TemplateSet builtin = TemplateSet.builtin();
        List<TemplateCompiler.Flat> flats = new ArrayList<>();
        for (UnitTemplate t : builtin.all()) {
            flats.add(new TemplateCompiler.Flat(t.id(), t.netIndex(), builtin.flattened(t.id())));
        }
        List<TemplateCompiler.TechFlat> techs =
                builtin.techs().stream().map(TemplateCompiler.TechFlat::of).toList();
        JsonObject json = builtin.civ(CivId.DEFAULT).toJson();
        json.add("rules", JsonParser.parseString(rules));
        RawCiv def = new RawCiv(
                CivId.DEFAULT, "default.json", builtin.civ(CivId.DEFAULT).toJson());
        TemplateCompiler.Result r =
                TemplateCompiler.fromFlattened(flats, techs, List.of(def, new RawCiv(VARIANT, "variant.json", json)));
        if (!r.problems().isEmpty()) {
            throw new AssertionError(r.problems().toString());
        }
        return r.set();
    }

    /** What faction 0 did over a run. */
    private static final class Log implements HeadlessMatch.Watcher {
        final List<Integer> workers = new ArrayList<>(); // every minute
        final List<Integer> soldiers = new ArrayList<>();
        final List<Integer> gathering = new ArrayList<>();
        int builders;
        int houses;
        int storehouses;
        int unfinishedLate;

        @Override
        public void tick(dev.strategos.core.testkit.SimHarness h, dev.strategos.core.sim.TickOutputs out) {
            long tick = out.tick();
            for (BuildingView b : h.core().queries().buildings()) {
                if (b.faction() != 0) {
                    continue;
                }
                if (b.template().equals(HOUSE)) {
                    houses++;
                }
                if (b.template().equals(STOREHOUSE)) {
                    storehouses++;
                }
            }
            if (tick % 20 != 0) {
                return;
            }
            int w = 0;
            int s = 0;
            int g = 0;
            for (int handle : h.core().queries().unitHandles()) {
                UnitView u = h.core().queries().unit(handle);
                if (u == null || u.faction().value() != 0) {
                    continue;
                }
                if (u.template().equals(TemplateSet.WORKER)) {
                    w++;
                }
                if (u.template().equals(TemplateSet.INFANTRY)) {
                    s++;
                }
                if (u.state() == UnitState.GATHERING) {
                    g++;
                }
                if (u.state() == UnitState.BUILDING) {
                    builders++;
                }
            }
            if (tick % MINUTE == 0) {
                workers.add(w);
                soldiers.add(s);
                gathering.add(g);
            }
            if (tick > 3 * MINUTE) {
                for (BuildingView b : h.core().queries().buildings()) {
                    if (b.faction() == 0 && b.state() == BuildingStore.FOUNDATION) {
                        unfinishedLate++;
                    }
                }
            }
        }
    }

    private static Log play(long seed, String rules) {
        Log log = new Log();
        String script = HeadlessMatch.read("ai/economy.json");
        HeadlessMatch.Summary m = HeadlessMatch.run(
                        HeadlessMatch.Ground.provingGround(),
                        HeadlessMatch.firstLoop(),
                        seed,
                        MINUTES_12,
                        List.of(script, script),
                        log,
                        false,
                        HeadlessMatch.EXPLORED,
                        variant(rules),
                        List.of(VARIANT, CivId.DEFAULT))
                .summary();
        assertThat(m.failure()).as("seed " + seed).isEmpty();
        return log;
    }

    /**
     * Faction 0 keeps growing: its workers reach the script's number, its army is larger at some minute after the fifth
     * than at the fifth, and it gathers in every minute from the third on.
     */
    private static void keepsGrowing(long seed, Log log) {
        int last = log.workers.size() - 1;
        assertThat(log.workers.get(last))
                .as("seed %d workers %s", seed, log.workers)
                .isGreaterThanOrEqualTo(20);
        assertThat(log.soldiers.subList(5, last + 1).stream()
                        .mapToInt(Integer::intValue)
                        .max()
                        .orElse(0))
                .as("seed %d soldiers %s", seed, log.soldiers)
                .isGreaterThan(log.soldiers.get(4));
        for (int minute = 3; minute <= last; minute++) {
            assertThat(log.gathering.get(minute))
                    .as("seed %d gatherers at minute %d: %s", seed, minute + 1, log.gathering)
                    .isPositive();
        }
    }

    @Test
    void aFixedPopulationNeedsNoHouseAndTheAiKeepsGrowing() {
        for (long seed : SEEDS) {
            Log log = play(seed, "{\"population\":{\"id\":\"fixed\",\"params\":{\"cap\":60}}}");
            assertThat(log.houses).as("seed %d: a house was built", seed).isZero();
            keepsGrowing(seed, log);
        }
    }

    @Test
    void directDeliveryNeedsNoDropsiteAndTheAiKeepsGrowing() {
        for (long seed : SEEDS) {
            Log log = play(seed, "{\"delivery\":{\"id\":\"direct\"}}");
            assertThat(log.storehouses)
                    .as("seed %d: a storehouse was built", seed)
                    .isZero();
            keepsGrowing(seed, log);
        }
    }

    @Test
    void selfBuildingFoundationsNeedNoBuilderAndTheAiKeepsGrowing() {
        for (long seed : SEEDS) {
            Log log = play(seed, "{\"construction\":{\"id\":\"self\",\"params\":{\"rate\":1}}}");
            assertThat(log.builders).as("seed %d: a builder built", seed).isZero();
            assertThat(log.unfinishedLate)
                    .as("seed %d: a foundation was left waiting for a builder after minute 3", seed)
                    .isZero(); // the rule commits it at placement, so it never waits
            keepsGrowing(seed, log);
        }
    }

    @Test
    void theDefaultRulesImplyEveryHintAndEachVariantClearsItsOwn() {
        assertThat(CivRules.DEFAULTS.hints()).isEqualTo(AiHint.ALL);
        assertThat(PopulationRule.DEFAULT.hints()).isEqualTo(AiHint.NEEDS_HOUSING.mask());
        assertThat(PopulationRule.housing(5).hints()).isEqualTo(AiHint.NEEDS_HOUSING.mask());
        assertThat(PopulationRule.fixed(30).hints()).isZero();
        assertThat(DeliveryRule.DEFAULT.hints()).isEqualTo(AiHint.NEEDS_DROPSITES.mask());
        assertThat(DeliveryRule.DIRECT.hints()).isZero();
        assertThat(ConstructionRule.DEFAULT.hints()).isEqualTo(AiHint.NEEDS_BUILDERS.mask());
        assertThat(ConstructionRule.selfBuilding(1).hints()).isZero();
        CivRules fixed = new CivRules(
                PopulationRule.fixed(30),
                DeliveryRule.DEFAULT,
                ConstructionRule.DEFAULT,
                CivRules.DEFAULTS.upkeep(),
                CivRules.DEFAULTS.resource(),
                CivRules.DEFAULTS.phase(),
                CivRules.DEFAULTS.placement(),
                CivRules.DEFAULTS.defeat());
        assertThat(fixed.needs(AiHint.NEEDS_HOUSING)).isFalse();
        assertThat(fixed.needs(AiHint.NEEDS_DROPSITES)).isTrue();
        assertThat(fixed.needs(AiHint.NEEDS_BUILDERS)).isTrue();
    }

    @Test
    void theAiViewReadsTheHintsOfItsFactionsRules() {
        dev.strategos.core.testkit.SimHarness h = dev.strategos.core.testkit.SimHarness.builder()
                .templates(variant("{\"population\":{\"id\":\"fixed\",\"params\":{\"cap\":30}}}"))
                .civs(List.of(VARIANT, CivId.DEFAULT))
                .factions(2)
                .size(64, 64)
                .yBand(0, 31, 1)
                .build();
        h.step();
        AiBudget budget = new AiBudget(AiBudget.CAP);
        AiView variant = new AiView(h.core().queries(), 0, budget);
        AiView standard = new AiView(h.core().queries(), 1, budget);
        assertThat(variant.needs(AiHint.NEEDS_HOUSING)).isFalse();
        assertThat(variant.needs(AiHint.NEEDS_DROPSITES)).isTrue();
        assertThat(standard.needs(AiHint.NEEDS_HOUSING)).isTrue();
        assertThat(h.core().queries().aiHints(new FactionSlot(1))).isEqualTo(AiHint.ALL);
    }
}
