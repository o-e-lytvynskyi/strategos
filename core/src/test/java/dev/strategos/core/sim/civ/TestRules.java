package dev.strategos.core.sim.civ;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.content.CivDef;
import dev.strategos.core.content.PlacementRule;
import dev.strategos.core.content.PlacementRules;
import dev.strategos.core.content.TemplateProblem;
import dev.strategos.core.shared.CivId;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.build.BuildingStore;
import java.util.ArrayList;
import java.util.List;

/**
 * Test-only variants, one per quiet seam (M10 T5), registered in a catalogue built with {@link RuleCatalogue#with}:
 * nothing of them is in {@code src/main}. Each proves its seam is honoured.
 */
final class TestRules {
    static final String BUILDINGS = "buildings";
    static final String UNITS = "units";
    static final String ANYWHERE = "anywhere";
    static final String SKIP = "skip";
    static final String INCOME = "income";

    /** The phase is the number of finished buildings the faction has, at most {@code cap} (parameter, default 3). */
    static final RuleFactory<PhaseRule> PHASE_BY_BUILDINGS = RuleFactory.of(BUILDINGS, PhaseRule.class, params -> {
        int cap = RuleParams.integer(params, "cap", 3);
        return (s, faction) -> {
            BuildingStore b = s.buildings();
            int n = 0;
            for (int handle : b.handles()) {
                if (b.faction(handle) == faction && b.state(handle) == BuildingStore.COMPLETE) {
                    n++;
                }
            }
            return Math.min(n, cap);
        };
    });

    /** A faction holds while any unit of it lives, buildings or none. */
    static final RuleFactory<DefeatRule> DEFEAT_BY_UNITS = RuleFactory.fixed(UNITS, DefeatRule.class, (s, faction) -> {
        for (int i = 0; i < s.entities().highWater(); i++) {
            if (s.entities().aliveAt(i) && s.identity().faction(i) == faction) {
                return true;
            }
        }
        return false;
    });

    /** Placement that ignores territory: the standard verdict with no territory rule. */
    static final PlacementRule IGNORES_TERRITORY = (world, b, at, faction, params) -> PlacementRules.standard(
            world,
            b,
            at,
            faction,
            new PlacementRules.Params(
                    params.civicCentre(), params.spacing(), PlacementRules.ANYWHERE, params.affordable()));

    static final RuleFactory<PlacementRule> PLACEMENT_ANYWHERE =
            RuleFactory.fixed(ANYWHERE, PlacementRule.class, IGNORES_TERRITORY);

    /** A faction that does not use the resource at index {@code index}. */
    static final RuleFactory<ResourceRule> RESOURCE_SKIP = RuleFactory.of(SKIP, ResourceRule.class, params -> {
        int index = RuleParams.requiredInteger(params, "index");
        return (faction, resource) -> resource != index;
    });

    /** Passive income: {@code amount} whole units of the resource at {@code index} each economy step, through grant. */
    static final RuleFactory<UpkeepRule> UPKEEP_INCOME = RuleFactory.of(INCOME, UpkeepRule.class, params -> {
        int index = RuleParams.requiredInteger(params, "index");
        int amount = RuleParams.requiredInteger(params, "amount");
        if (index < 0 || amount <= 0) {
            throw new RuleFactory.BadParams("rule 'income' needs an index and a positive amount, got " + params);
        }
        return (MatchState s, int faction) -> s.treasury().grant(faction, index, amount);
    });

    private TestRules() {}

    static RuleCatalogue catalogue() {
        return RuleCatalogue.DEFAULT
                .with(SeamKind.PHASE, PHASE_BY_BUILDINGS)
                .with(SeamKind.DEFEAT, DEFEAT_BY_UNITS)
                .with(SeamKind.PLACEMENT, PLACEMENT_ANYWHERE)
                .with(SeamKind.RESOURCES, RESOURCE_SKIP)
                .with(SeamKind.UPKEEP, UPKEEP_INCOME);
    }

    /** A civilisation file with a house role and {@code rules} (a JSON object of seam to {id, params}). */
    static JsonObject civ(String rules) {
        return JsonParser.parseString("{\"roles\":{\"house\":\"strategos:building/house\"},\"rules\":" + rules + "}")
                .getAsJsonObject();
    }

    /** The decoded civilisation {@code t:rules} of {@link #civ}. */
    static CivDef def(String rules) {
        List<TemplateProblem> problems = new ArrayList<>();
        CivDef def = CivDef.fromJson(new CivId("t:rules"), civ(rules), "t.json", problems);
        if (def == null) {
            throw new AssertionError(problems.toString());
        }
        return def;
    }
}
