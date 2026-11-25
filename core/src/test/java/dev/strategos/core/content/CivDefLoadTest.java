package dev.strategos.core.content;

import static dev.strategos.core.content.CivTestSupport.MINIMAL;
import static dev.strategos.core.content.CivTestSupport.civ;
import static dev.strategos.core.content.CivTestSupport.compile;
import static dev.strategos.core.content.CivTestSupport.obj;
import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.shared.CivId;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.shared.TemplateId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** CIV-01: a civilisation file decodes, compiles into the set and has one canonical JSON. */
class CivDefLoadTest {
    private static final CivId ID = new CivId("t:tribe");
    private static final String FILE = "data/t/strategos/civs/tribe.json";

    private static final String FULL = """
            {
              "name": "civ.t.tribe",
              "phases": ["phase.t.camp", "phase.t.hold"],
              "roster": { "templates": ["strategos:unit/worker", "strategos:building/house", "strategos:building/civic_centre"],
                          "techs": ["strategos:carry_1"] },
              "centreClass": "CivicCentre",
              "roles": {
                "worker": "strategos:unit/worker",
                "house": "strategos:building/house",
                "centre": "strategos:building/civic_centre"
              },
              "start": {
                "resources": { "wood": 200, "food": 50 },
                "entities": [
                  { "role": "centre" },
                  { "role": "worker", "dz": 9, "count": 3, "spread": 1.5, "rotation": 2 },
                  { "template": "strategos:building/house", "dx": 7 }
                ]
              },
              "bonuses": [ { "path": "UnitMotion/walkSpeed", "op": "mul", "value": 1.1, "filter": "Worker" } ],
              "rules": { "population": { "id": "fixed", "params": { "cap": 40 } }, "delivery": { "id": "default" } },
              "ai": { "profile": "raider" }
            }
            """;

    private static CivDef decode(String json) {
        List<TemplateProblem> problems = new ArrayList<>();
        CivDef def = CivDef.fromJson(ID, obj(json), FILE, problems);
        assertThat(problems).isEmpty();
        assertThat(def).isNotNull();
        return def;
    }

    @Test
    void aFullCivilisationDecodesEveryField() {
        CivDef def = decode(FULL);

        assertThat(def.id()).isEqualTo(ID);
        assertThat(def.name()).isEqualTo("civ.t.tribe");
        assertThat(def.phases()).containsExactly("phase.t.camp", "phase.t.hold");
        assertThat(def.roster().templates())
                .containsExactly(
                        new TemplateId("strategos:building/civic_centre"),
                        new TemplateId("strategos:building/house"),
                        new TemplateId("strategos:unit/worker"));
        assertThat(def.roster().techs()).containsExactly(new TechId("strategos:carry_1"));
        assertThat(def.centreClass()).isEqualTo("CivicCentre");
        assertThat(def.roles().byRole().keySet()).containsExactly("centre", "house", "worker");
        assertThat(def.roles().get(CivRoles.WORKER)).isEqualTo(new TemplateId("strategos:unit/worker"));
        assertThat(def.start().resources()).containsEntry("wood", 200L).containsEntry("food", 50L);
        assertThat(def.start().entities()).hasSize(3);
        assertThat(def.start().entities().get(1)).isEqualTo(new StartKit.Entity("worker", null, 0, 9, 3, 1.5, 2));
        assertThat(def.start().entities().get(2).template()).isEqualTo(new TemplateId("strategos:building/house"));
        assertThat(def.bonuses())
                .containsExactly(new TechEffect("UnitMotion/walkSpeed", TechEffect.Op.MUL, 1.1, null, "Worker", 0, 0));
        assertThat(def.rules()).containsOnlyKeys("delivery", "population");
        assertThat(def.rules().get("population").id()).isEqualTo("fixed");
        assertThat(def.rules().get("population").params().get("cap").getAsInt()).isEqualTo(40);
        assertThat(def.rules().get("delivery").params().size()).isZero();
        assertThat(def.aiProfile()).isEqualTo("raider");
    }

    @Test
    void aBareCivilisationTakesTheDocumentedDefaults() {
        CivDef def = decode("{}");

        assertThat(def.name()).as("the id").isEqualTo("t:tribe");
        assertThat(def.phases()).isEmpty();
        assertThat(def.roster()).isEqualTo(CivDef.Roster.ALL);
        assertThat(def.centreClass()).isEqualTo("CivicCentre");
        assertThat(def.roles()).isEqualTo(CivRoles.NONE);
        assertThat(def.start()).isEqualTo(StartKit.EMPTY);
        assertThat(def.bonuses()).isEmpty();
        assertThat(def.rules()).isEmpty();
        assertThat(def.aiProfile()).isEqualTo("skirmish");
        assertThat(def.toJson().has("phases")).as("no phases, none written").isFalse();
    }

    @Test
    void theWildcardRosterHasEverythingAndAListHasOnlyItself() {
        CivDef all = decode("{\"roster\":{\"templates\":\"*\",\"techs\":\"*\"}}");
        CivDef some = decode("{\"roster\":{\"templates\":[\"strategos:unit/worker\"],\"techs\":[]}}");

        assertThat(all.roster()).isEqualTo(CivDef.Roster.ALL);
        assertThat(all.roster().hasTemplate(new TemplateId("any:thing"))).isTrue();
        assertThat(all.roster().hasTech(new TechId("any:tech"))).isTrue();
        assertThat(all.toJson().getAsJsonObject("roster").get("templates").getAsString())
                .isEqualTo("*");
        assertThat(some.roster().hasTemplate(new TemplateId("strategos:unit/worker")))
                .isTrue();
        assertThat(some.roster().hasTemplate(new TemplateId("strategos:unit/infantry")))
                .isFalse();
        assertThat(some.roster().hasTech(new TechId("strategos:carry_1"))).isFalse();
    }

    @Test
    void theCanonicalJsonReadsBackToTheSameCivilisationWhateverTheSpellingOrOrder() {
        CivDef def = decode(FULL);
        CivDef back = decode(def.toJson().toString());
        CivDef spelledOut = decode("{\"ai\":{\"profile\":\"skirmish\"},\"rules\":{},\"bonuses\":[],\"centreClass\":"
                + "\"CivicCentre\",\"roster\":{\"techs\":\"*\",\"templates\":\"*\"}}");

        assertThat(back).isEqualTo(def);
        assertThat(CanonicalJson.write(back.toJson())).isEqualTo(CanonicalJson.write(def.toJson()));
        assertThat(CanonicalJson.write(spelledOut.toJson()))
                .isEqualTo(CanonicalJson.write(decode("{}").toJson()));
        assertThat(def.toJson().keySet())
                .containsExactlyInAnyOrder(
                        "name", "phases", "roster", "centreClass", "roles", "start", "bonuses", "rules", "ai");
        assertThat(def.toJson()
                        .getAsJsonObject("start")
                        .getAsJsonArray("entities")
                        .get(0)
                        .getAsJsonObject()
                        .keySet())
                .as("an entity writes its defaults")
                .containsExactlyInAnyOrder("role", "dx", "dz", "count", "spread", "rotation");
    }

    @Test
    void civilisationsCompileIntoTheSetAscendingByIdAndAreLookedUpById() {
        TemplateCompiler.Result result =
                compile(civ("t:zulu", MINIMAL), civ("t:alpha", MINIMAL), civ("strategos:default", MINIMAL));

        assertThat(result.problems()).isEmpty();
        TemplateSet set = result.set();
        assertThat(set.civs())
                .extracting(c -> c.id().value())
                .containsExactly("strategos:default", "t:alpha", "t:zulu");
        assertThat(set.civ(new CivId("t:alpha"))).isNotNull();
        assertThat(set.civ(new CivId("t:ghost"))).isNull();
    }

    @Test
    void aSetWithNoCivilisationFilesHasTheImpliedDefault() {
        TemplateSet set = compile().set();

        assertThat(set.hasImpliedCivs()).isTrue();
        assertThat(set.civs()).containsExactly(BuiltinTemplates.DEFAULT_CIV);
        assertThat(set.civ(new CivId("strategos:default"))).isSameAs(BuiltinTemplates.DEFAULT_CIV);
    }

    @Test
    void theBuiltInDefaultIsTodaysContent() {
        CivDef def = TemplateSet.builtin().civ(new CivId("strategos:default"));

        assertThat(def).isNotNull();
        assertThat(def.name()).isEqualTo("strategos.civ.default");
        assertThat(def.phases()).hasSize(3);
        assertThat(def.roster()).isEqualTo(CivDef.Roster.ALL);
        assertThat(def.centreClass()).isEqualTo("CivicCentre");
        assertThat(def.roles().byRole()).hasSize(16);
        assertThat(def.start().resources()).containsOnlyKeys("food", "iron", "stone");
        assertThat(def.bonuses()).isEmpty();
        assertThat(def.rules()).isEmpty();
        assertThat(def.aiProfile()).isEqualTo("skirmish");
    }
}
