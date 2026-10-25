package dev.strategos.core.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.sim.stats.ClassFilter;
import dev.strategos.core.sim.stats.Modifier;
import dev.strategos.core.sim.stats.ModifierOp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TechDefTest {
    private static final String FILE = "data/t/strategos/techs/axes.json";
    private static final TechId AXES = new TechId("t:axes");

    private static final String VALID = """
            {
              "name": "Felling axes",
              "icon": "minecraft:iron_axe",
              "classes": ["Economy"],
              "cost": { "food": 100, "wood": 50 },
              "time": 30,
              "requires": { "all": [ { "phase": "phase_village" }, { "buildings": { "filter": "House", "count": 2 } } ] },
              "supersedes": "t:old_axes",
              "effects": [
                { "path": "ResourceGatherer/rates/wood/tree", "op": "mul", "value": 1.2, "filter": "Worker" },
                { "path": "Identity/classes", "op": "tokens", "tokens": "+Axed", "stage": 1, "priority": 2 }
              ]
            }
            """;

    private static RawTech tech(String id, String json) {
        return new RawTech(new TechId(id), "data/t/strategos/techs/" + id.substring(2) + ".json", obj(json));
    }

    private static JsonObject obj(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static List<TemplateProblem> problemsOf(String json) {
        List<TemplateProblem> problems = new ArrayList<>();
        TechDef def = TechDef.fromJson(AXES, 0, obj(json), FILE, problems);
        if (!problems.isEmpty()) {
            assertThat(def).isNull();
        }
        return problems;
    }

    @Test
    void aValidTechDecodesAndRoundsItsTimeOnceLikeATemplate() {
        List<TemplateProblem> problems = new ArrayList<>();
        TechDef def = TechDef.fromJson(AXES, 3, obj(VALID), FILE, problems);

        assertThat(problems).isEmpty();
        assertThat(def).isNotNull();
        assertThat(def.netIndex()).isEqualTo(3);
        assertThat(def.name()).isEqualTo("Felling axes");
        assertThat(def.classes()).containsExactly("Economy");
        assertThat(def.cost()).isEqualTo(Map.of("food", 100.0, "wood", 50.0));
        assertThat(def.ticks()).as("30 s at 20 ticks").isEqualTo(600);
        assertThat(def.supersedes()).isEqualTo(new TechId("t:old_axes"));
        assertThat(def.requires())
                .isEqualTo(new Requirement.All(
                        List.of(new Requirement.Phase("phase_village"), new Requirement.Buildings("House", 2))));
        assertThat(def.effects())
                .containsExactly(
                        new TechEffect(
                                "ResourceGatherer/rates/wood/tree", TechEffect.Op.MUL, 1.2, null, "Worker", 0, 0),
                        new TechEffect("Identity/classes", TechEffect.Op.TOKENS, 0, "+Axed", "", 1, 2));
        assertThat(obj(def.toJson().toString()))
                .as("the JSON form decodes to the same tech")
                .isNotNull();
        List<TemplateProblem> again = new ArrayList<>();
        assertThat(TechDef.fromJson(AXES, 3, def.toJson(), FILE, again)).isEqualTo(def);
        assertThat(again).isEmpty();
    }

    @Test
    void ticksRoundHalfUpOnceAndNeverBelowOne() {
        List<TemplateProblem> p = new ArrayList<>();
        assertThat(TechDef.fromJson(AXES, 0, obj("{\"icon\":\"i\",\"time\":0.026}"), FILE, p)
                        .ticks())
                .as("0.52 ticks rounds to 1")
                .isEqualTo(1);
        assertThat(TechDef.fromJson(AXES, 0, obj("{\"icon\":\"i\",\"time\":0.01}"), FILE, p)
                        .ticks())
                .isEqualTo(1);
        assertThat(TechDef.fromJson(AXES, 0, obj("{\"icon\":\"i\",\"time\":1.025}"), FILE, p)
                        .ticks())
                .as("20.5 ticks rounds half up, as Cost/time does")
                .isEqualTo(21);
    }

    @Test
    void eachMalformedCaseNamesTheFileAndThePath() {
        String ok = "\"icon\": \"i\", \"time\": 5";
        record Bad(String json, String pointer) {}
        List<Bad> cases = List.of(
                new Bad("{" + ok + ", \"colour\": 1}", "/colour"),
                new Bad(
                        "{" + ok + ", \"effects\": [ { \"path\": \"a/b\", \"op\": \"scale\", \"value\": 2 } ] }",
                        "/effects/0/op"),
                new Bad(
                        "{" + ok + ", \"effects\": [ { \"path\": \"a/b\", \"op\": \"tokens\" } ] }",
                        "/effects/0/tokens"),
                new Bad("{" + ok + ", \"effects\": [ { \"path\": \"a/b\", \"op\": \"mul\" } ] }", "/effects/0/value"),
                new Bad(
                        "{" + ok
                                + ", \"effects\": [ { \"path\": \"a/b\", \"op\": \"mul\", \"value\": 2, \"filter\": \"A++B\" } ] }",
                        "/effects/0/filter"),
                new Bad(
                        "{" + ok
                                + ", \"effects\": [ { \"path\": \"a/b\", \"op\": \"mul\", \"value\": 2, \"extra\": 1 } ] }",
                        "/effects/0/extra"),
                new Bad("{" + ok + ", \"requires\": { \"tech\": \"t:a\", \"phase\": \"p\" } }", "/requires"),
                new Bad(
                        "{" + ok + ", \"requires\": { \"all\": [ { \"tech\": \"t:a\", \"phase\": \"p\" } ] } }",
                        "/requires/all/0"),
                new Bad(
                        "{" + ok + ", \"requires\": { \"buildings\": { \"filter\": \"House\", \"count\": 0 } } }",
                        "/requires/buildings/count"),
                new Bad(
                        "{" + ok + ", \"requires\": { \"buildings\": { \"filter\": \"+\", \"count\": 1 } } }",
                        "/requires/buildings/filter"),
                new Bad("{" + ok + ", \"requires\": { \"wonders\": 1 } }", "/requires/wonders"),
                new Bad("{\"icon\": \"\", \"time\": 5}", "/icon"),
                new Bad("{\"time\": 5}", "/icon"),
                new Bad("{\"icon\": \"i\", \"time\": 0}", "/time"),
                new Bad("{" + ok + ", \"cost\": { \"food\": -1 } }", "/cost/food"));
        for (Bad bad : cases) {
            assertThat(problemsOf(bad.json)).as(bad.json).anySatisfy(p -> {
                assertThat(p.file()).isEqualTo(FILE);
                assertThat(p.pointer()).isEqualTo(bad.pointer);
                assertThat(p.severity()).isEqualTo(TemplateProblem.Severity.ERROR);
            });
        }
    }

    @Test
    void anUnknownRequiredOrSupersededTechIsACompileError() {
        TemplateCompiler.Result result = TemplateCompiler.compile(
                List.of(),
                List.of(
                        tech(
                                "t:axes",
                                "{\"icon\":\"i\",\"time\":5,\"requires\":{\"any\":[{\"tech\":\"t:ghost\"}]},"
                                        + "\"supersedes\":\"t:phantom\"}"),
                        tech("t:plough", "{\"icon\":\"i\",\"time\":5,\"requires\":{\"tech\":\"t:axes\"}}")),
                TemplateIdTable.EMPTY,
                TechIdTable.EMPTY,
                PlaceholderBlueprints.byId());

        assertThat(result.problems())
                .extracting(TemplateProblem::file, TemplateProblem::pointer)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("data/t/strategos/techs/axes.json", "/requires/any/0/tech"),
                        org.assertj.core.groups.Tuple.tuple("data/t/strategos/techs/axes.json", "/supersedes"));
        assertThat(result.set().techs())
                .as("the tech with a bad reference is left out")
                .extracting(t -> t.id().value())
                .containsExactly("t:plough");
    }

    @Test
    void techIndicesAreAppendOnlyAcrossCompiles() {
        RawTech a = tech("t:axes", "{\"icon\":\"i\",\"time\":5}");
        RawTech b = tech("t:bows", "{\"icon\":\"i\",\"time\":5}");
        RawTech c = tech("t:carts", "{\"icon\":\"i\",\"time\":5}");
        TemplateCompiler.Result first = TemplateCompiler.compile(
                List.of(), List.of(b, c), TemplateIdTable.EMPTY, TechIdTable.EMPTY, PlaceholderBlueprints.byId());
        TemplateCompiler.Result second = TemplateCompiler.compile(
                List.of(), List.of(a, c), TemplateIdTable.EMPTY, first.techTable(), PlaceholderBlueprints.byId());

        assertThat(first.set().tech(new TechId("t:bows")).netIndex()).isZero();
        assertThat(second.set().tech(new TechId("t:carts")).netIndex())
                .as("kept")
                .isEqualTo(1);
        assertThat(second.set().tech(new TechId("t:axes")).netIndex())
                .as("appended after the retired")
                .isEqualTo(2);
        assertThat(second.set().techByIndex(0)).as("a retired index").isNull();
        assertThat(second.set().techByIndex(2).id()).isEqualTo(new TechId("t:axes"));
    }

    @Test
    void theContentLayersSyntaxChecksAgreeWithTheStatServices() {
        for (String filter : List.of("", "Worker", "A+B !C", "A++B", "+A", "!", "A+!B", "!!A", "A+", " ", "A  B")) {
            boolean ours = StatSyntax.validFilter(filter);
            boolean theirs;
            try {
                ClassFilter.compile(filter, new ClassTokens(List.of()));
                theirs = true;
            } catch (IllegalArgumentException e) {
                theirs = false;
            }
            assertThat(ours).as("filter '" + filter + "'").isEqualTo(theirs);
        }
        for (String edit : List.of("+a", "+a -b", "", " ", "a", "+", "++a", "+-a", "-a +b", "+a b")) {
            boolean ours = StatSyntax.validTokenEdit(edit);
            boolean theirs;
            try {
                new Modifier("p", ModifierOp.TOKENS, 0, edit, "", 0, 0, 0);
                theirs = true;
            } catch (IllegalArgumentException e) {
                theirs = false;
            }
            assertThat(ours).as("edit '" + edit + "'").isEqualTo(theirs);
        }
        for (TechEffect.Op op : TechEffect.Op.values()) {
            assertThat(ModifierOp.valueOf(op.name()).name()).isEqualTo(op.name());
        }
        assertThat(TechEffect.Op.values()).hasSameSizeAs(ModifierOp.values());
    }
}
