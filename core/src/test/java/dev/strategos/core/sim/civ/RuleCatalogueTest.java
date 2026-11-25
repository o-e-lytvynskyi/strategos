package dev.strategos.core.sim.civ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.content.CivDef;
import dev.strategos.core.content.TemplateProblem;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.CivId;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CIV-05: the catalogue is closed and its ids are forever (a changed behaviour is a new id); its lint names the seam, the
 * id and the parameters with a file and a JSON pointer.
 */
class RuleCatalogueTest {
    private static CivDef civ(String rules) {
        return TestRules.def(rules);
    }

    @Test
    void theDefaultCataloguePinsItsIds() {
        // Removing an id breaks the journals recorded with it: add ids (name@2 for a changed behaviour), never remove.
        for (SeamKind k : SeamKind.values()) {
            assertThat(RuleCatalogue.DEFAULT.ids(k))
                    .as(k.dataName())
                    .containsExactlyElementsOf(
                            switch (k) {
                                case POPULATION -> List.of("default", "fixed");
                                case DELIVERY -> List.of("default", "direct");
                                case CONSTRUCTION -> List.of("default", "self");
                                default -> List.of("default");
                            });
        }
        assertThat(SeamKind.values())
                .extracting(SeamKind::dataName)
                .containsExactly(
                        "population",
                        "delivery",
                        "construction",
                        "upkeep",
                        "resources",
                        "phase",
                        "placement",
                        "defeat");
    }

    @Test
    void withWidensACatalogueAndLeavesTheDefaultAlone() {
        RuleCatalogue wider = TestRules.catalogue();

        assertThat(wider.ids(SeamKind.PHASE)).containsExactly("buildings", "default");
        assertThat(wider.ids(SeamKind.UPKEEP)).containsExactly("default", "income");
        assertThat(RuleCatalogue.DEFAULT.ids(SeamKind.PHASE)).containsExactly("default");
    }

    @Test
    void withRefusesAFactoryOfAnotherSeamAndADuplicateId() {
        assertThatThrownBy(() -> RuleCatalogue.DEFAULT.with(SeamKind.PHASE, TestRules.DEFEAT_BY_UNITS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("phase seam");
        RuleCatalogue wider = RuleCatalogue.DEFAULT.with(SeamKind.PHASE, TestRules.PHASE_BY_BUILDINGS);
        assertThatThrownBy(() -> wider.with(SeamKind.PHASE, TestRules.PHASE_BY_BUILDINGS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already has");
    }

    @Test
    void aCivilisationWithoutRulesOrWithDefaultsIsLintClean() {
        assertThat(RuleCatalogue.DEFAULT.problems(TemplateSet.builtin().civ(CivId.DEFAULT)))
                .isEmpty();
        assertThat(RuleCatalogue.DEFAULT.problems(
                        civ("{\"phase\":{\"id\":\"default\"},\"population\":{\"id\":\"default\"}}")))
                .isEmpty();
    }

    @Test
    void anUnknownSeamIsReportedWithItsPointer() {
        List<TemplateProblem> problems =
                RuleCatalogue.DEFAULT.problems(civ("{\"morale\":{\"id\":\"default\"}}"), "f.json");

        assertThat(problems).hasSize(1);
        assertThat(problems.get(0).file()).isEqualTo("f.json");
        assertThat(problems.get(0).pointer()).isEqualTo("/rules/morale");
        assertThat(problems.get(0).message()).contains("unknown seam 'morale'").contains("phase");
    }

    @Test
    void anUnknownIdIsReportedAndNamesTheKnownOnes() {
        List<TemplateProblem> problems =
                RuleCatalogue.DEFAULT.problems(civ("{\"phase\":{\"id\":\"buildings\"}}"), "f.json");

        assertThat(problems).hasSize(1);
        assertThat(problems.get(0).pointer()).isEqualTo("/rules/phase/id");
        assertThat(problems.get(0).message())
                .contains("unknown rule 'buildings'")
                .contains("known: default");
        assertThat(TestRules.catalogue().problems(civ("{\"phase\":{\"id\":\"buildings\"}}")))
                .isEmpty();
    }

    @Test
    void badParametersAreReportedByTheFactory() {
        RuleCatalogue wider = TestRules.catalogue();

        List<TemplateProblem> noParams =
                RuleCatalogue.DEFAULT.problems(civ("{\"phase\":{\"id\":\"default\",\"params\":{\"x\":1}}}"));
        assertThat(noParams).hasSize(1);
        assertThat(noParams.get(0).pointer()).isEqualTo("/rules/phase/params");
        assertThat(noParams.get(0).message()).contains("takes no parameters");

        List<TemplateProblem> bad =
                wider.problems(civ("{\"upkeep\":{\"id\":\"income\",\"params\":{\"index\":\"wood\"}}}"));
        assertThat(bad).hasSize(1);
        assertThat(bad.get(0).message()).contains("/params/index").contains("expected an integer");

        assertThat(wider.problems(civ("{\"upkeep\":{\"id\":\"income\"}}"))).hasSize(1);
        assertThat(wider.problems(civ("{\"upkeep\":{\"id\":\"income\",\"params\":{\"index\":1,\"amount\":2}}}")))
                .isEmpty();
    }

    @Test
    void theFixedPopulationRuleNeedsAnIntegerCapAndTheDefaultAnOptionalBase() {
        RuleCatalogue c = RuleCatalogue.DEFAULT;
        assertThat(c.problems(civ("{\"population\":{\"id\":\"fixed\",\"params\":{\"cap\":30}}}")))
                .isEmpty();
        assertThat(c.problems(civ("{\"population\":{\"id\":\"default\",\"params\":{\"base\":5}}}")))
                .isEmpty();
        assertThat(c.problems(civ("{\"delivery\":{\"id\":\"direct\"}}"))).isEmpty();

        List<TemplateProblem> missing = c.problems(civ("{\"population\":{\"id\":\"fixed\"}}"), "f.json");
        assertThat(missing).hasSize(1);
        assertThat(missing.get(0).pointer()).isEqualTo("/rules/population/params");
        assertThat(missing.get(0).message()).contains("/params/cap").contains("required");
        for (String bad : List.of("\"x\"", "{}", "[1]", "1.5", "true", "null", "-1")) {
            assertThat(c.problems(civ("{\"population\":{\"id\":\"fixed\",\"params\":{\"cap\":" + bad + "}}}")))
                    .as("cap %s", bad)
                    .hasSize(1);
        }
        for (String bad : List.of("\"x\"", "[1]", "0.5", "-2")) {
            assertThat(c.problems(civ("{\"population\":{\"id\":\"default\",\"params\":{\"base\":" + bad + "}}}")))
                    .as("base %s", bad)
                    .hasSize(1);
        }
        assertThat(c.problems(civ("{\"delivery\":{\"id\":\"direct\",\"params\":{\"x\":1}}}")))
                .as("direct takes none")
                .hasSize(1);
    }

    @Test
    void theSelfConstructionRuleNeedsAPositiveRate() {
        RuleCatalogue c = RuleCatalogue.DEFAULT;
        assertThat(c.problems(civ("{\"construction\":{\"id\":\"self\",\"params\":{\"rate\":0.5}}}")))
                .isEmpty();
        assertThat(c.problems(civ("{\"construction\":{\"id\":\"self\",\"params\":{\"rate\":3}}}")))
                .isEmpty();
        List<TemplateProblem> missing = c.problems(civ("{\"construction\":{\"id\":\"self\"}}"), "f.json");
        assertThat(missing).hasSize(1);
        assertThat(missing.get(0).pointer()).isEqualTo("/rules/construction/params");
        assertThat(missing.get(0).message()).contains("/params/rate").contains("required");
        for (String bad : List.of("\"x\"", "{}", "[1]", "0", "-1", "true", "null", "1e-9", "1e12")) {
            assertThat(c.problems(civ("{\"construction\":{\"id\":\"self\",\"params\":{\"rate\":" + bad + "}}}")))
                    .as("rate %s", bad)
                    .hasSize(1);
        }
        assertThat(c.problems(civ("{\"construction\":{\"id\":\"default\",\"params\":{\"rate\":1}}}")))
                .as("default takes none")
                .hasSize(1);
    }

    @Test
    void resolveRefusesWhatLintRefuses() {
        assertThatThrownBy(() -> RuleCatalogue.DEFAULT.resolve(civ("{\"phase\":{\"id\":\"nope\"}}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown rule 'nope'");
        assertThat(RuleCatalogue.DEFAULT.resolve(null)).isSameAs(CivRules.DEFAULTS);
        assertThat(RuleCatalogue.DEFAULT.resolve(civ("{}"))).isEqualTo(CivRules.DEFAULTS);
    }

    @Test
    void wrongShapedParametersAreReportedNeverThrown() {
        RuleCatalogue wider = TestRules.catalogue();
        for (String cap : List.of("\"x\"", "{}", "[1]", "1.5", "true", "null")) {
            List<TemplateProblem> problems = wider.problems(
                    civ("{\"phase\":{\"id\":\"buildings\",\"params\":{\"cap\":" + cap + "}}}"), "f.json");
            assertThat(problems).as("cap %s", cap).hasSize(1);
            assertThat(problems.get(0).pointer()).isEqualTo("/rules/phase/params");
            assertThat(problems.get(0).message()).contains("/params/cap");
        }
        assertThat(wider.problems(civ("{\"phase\":{\"id\":\"buildings\",\"params\":{\"cap\":2}}}")))
                .isEmpty();
    }

    @Test
    void aDecoderThatThrowsGsonsOwnExceptionsStillGivesALintLine() {
        RuleFactory<PhaseRule> careless = RuleFactory.of("careless", PhaseRule.class, params -> {
            int n = params.get("n").getAsInt(); // NPE, ClassCastException, IllegalStateException on bad shapes
            return (s, f) -> n;
        });
        RuleCatalogue wider = RuleCatalogue.DEFAULT.with(SeamKind.PHASE, careless);
        for (String params : List.of("{}", "{\"n\":{}}", "{\"n\":[1,2]}", "{\"n\":\"x\"}")) {
            List<TemplateProblem> problems =
                    wider.problems(civ("{\"phase\":{\"id\":\"careless\",\"params\":" + params + "}}"), "f.json");
            assertThat(problems).as(params).hasSize(1);
            assertThat(problems.get(0).message()).contains("careless");
        }
        assertThatThrownBy(() -> wider.resolve(civ("{\"phase\":{\"id\":\"careless\",\"params\":{}}}")))
                .isInstanceOf(RuleFactory.BadParams.class);
    }

    @Test
    void paramsThatAreNotAnObjectNeverReachTheCatalogue() {
        List<TemplateProblem> problems = new java.util.ArrayList<>();
        CivDef def = CivDef.fromJson(
                new CivId("t:x"), TestRules.civ("{\"phase\":{\"id\":\"default\",\"params\":[1]}}"), "x.json", problems);

        assertThat(def).isNull();
        assertThat(problems).extracting(TemplateProblem::pointer).contains("/rules/phase/params");
    }
}
