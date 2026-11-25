package dev.strategos.mod.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.content.CivDef;
import dev.strategos.core.content.PlacementRule;
import dev.strategos.core.content.PlacementRules;
import dev.strategos.core.content.TemplateProblem;
import dev.strategos.core.shared.CivId;
import dev.strategos.core.sim.civ.CivRules;
import dev.strategos.core.sim.civ.RuleCatalogue;
import dev.strategos.core.sim.civ.RuleFactory;
import dev.strategos.core.sim.civ.SeamKind;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CIV-05: the client's rules are built from its civilisation through the catalogue, the path the preview takes, not a
 * hand-made lambda; a civilisation the catalogue refuses plays on the defaults and says so.
 */
class ViewerRulesTest {
    private static final PlacementRule IGNORES_TERRITORY = (world, b, at, faction, params) -> PlacementRules.standard(
            world,
            b,
            at,
            faction,
            new PlacementRules.Params(
                    params.civicCentre(), params.spacing(), PlacementRules.ANYWHERE, params.affordable()));

    private static final RuleCatalogue CATALOGUE = RuleCatalogue.DEFAULT.with(
            SeamKind.PLACEMENT, RuleFactory.fixed("anywhere", PlacementRule.class, IGNORES_TERRITORY));

    private static CivDef civ(String placementId) {
        JsonObject json = JsonParser.parseString("{\"roles\":{\"house\":\"strategos:building/house\"},"
                        + "\"rules\":{\"placement\":{\"id\":\"" + placementId + "\"}}}")
                .getAsJsonObject();
        List<TemplateProblem> problems = new ArrayList<>();
        CivDef def = CivDef.fromJson(new CivId("t:viewer"), json, "v.json", problems);
        assertThat(def).as(problems.toString()).isNotNull();
        return def;
    }

    @Test
    void aCivilisationWithAPlacementVariantMakesTheClientsPlacementRule() {
        List<String> warnings = new ArrayList<>();

        CivRules rules = ViewerRules.resolve(CATALOGUE, civ("anywhere"), warnings::add);

        assertThat(rules.placement()).isSameAs(IGNORES_TERRITORY);
        assertThat(warnings).isEmpty();
        assertThat(ViewerRules.resolve(CATALOGUE, civ("default"), warnings::add).placement())
                .isSameAs(PlacementRule.DEFAULT);
    }

    @Test
    void aMissingCivilisationIsEveryDefaultWithoutAWarning() {
        List<String> warnings = new ArrayList<>();

        assertThat(ViewerRules.resolve(CATALOGUE, null, warnings::add)).isEqualTo(CivRules.DEFAULTS);
        assertThat(warnings).isEmpty();
    }

    @Test
    void aRuleTheCatalogueRefusesFallsBackToTheDefaultsAndWarns() {
        List<String> warnings = new ArrayList<>();

        CivRules rules = ViewerRules.resolve(RuleCatalogue.DEFAULT, civ("anywhere"), warnings::add);

        assertThat(rules).isEqualTo(CivRules.DEFAULTS);
        assertThat(warnings)
                .singleElement()
                .asString()
                .contains("t:viewer")
                .contains("unknown rule 'anywhere'")
                .contains("defaults");
    }
}
