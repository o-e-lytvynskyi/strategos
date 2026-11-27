package dev.strategos.core.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.content.AbilityDef.Aim;
import dev.strategos.core.shared.TemplateId;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * UNIT-10.4: {@code Abilities/<id>} is linted at compile time: every defect is a {@link TemplateProblem} at the JSON
 * pointer of the field, never an exception, whatever the shape of the data; the ability a set compiles reads back as
 * written.
 */
class AbilityLintTest {
    private static final String UNIT = """
            {"Identity":{},"UnitMotion":{"walkSpeed":0.25},"Capture":{"strength":2},
             "Body":{"entityType":"strategos:unit_humanoid","width":0.6,"height":1.8}}
            """;

    private static final String HEAL = "{\"kind\":\"heal\",\"params\":{\"amount\":5}}";

    private static RawTemplate raw(String id, String file, String abilities) {
        JsonObject json = JsonParser.parseString(UNIT).getAsJsonObject();
        if (abilities != null) {
            json.add("Abilities", JsonParser.parseString(abilities));
        }
        return new RawTemplate(new TemplateId(id), file, json);
    }

    private static TemplateCompiler.Result compile(String abilities) {
        return TemplateCompiler.compile(List.of(raw("strategos:unit/test", "test.json", abilities)));
    }

    private static String ability(String fields) {
        return "{\"x\":{" + fields + "}}";
    }

    private static String ability(String target, String rest, String effects) {
        return ability("\"target\":\"" + target + "\"" + (rest.isEmpty() ? "" : "," + rest) + ",\"effects\":[" + effects
                + "]");
    }

    static Stream<Arguments> defects() {
        return Stream.of(
                Arguments.of(
                        "an unknown key", ability("none", "\"colour\":1", HEAL), "/Abilities/x/colour", "unknown key"),
                Arguments.of(
                        "a missing target",
                        ability("\"effects\":[" + HEAL + "]"),
                        "/Abilities/x/target",
                        "expected none"),
                Arguments.of(
                        "an unknown target", ability("sideways", "", HEAL), "/Abilities/x/target", "expected none"),
                Arguments.of(
                        "a negative range",
                        ability("unit", "\"range\":-1", HEAL),
                        "/Abilities/x/range",
                        "expected a number"),
                Arguments.of(
                        "a range past the cap",
                        ability("unit", "\"range\":1000", HEAL),
                        "/Abilities/x/range",
                        "expected a number"),
                Arguments.of(
                        "a radius on a unit ability",
                        ability("unit", "\"radius\":2", HEAL),
                        "/Abilities/x/radius",
                        "only an area"),
                Arguments.of(
                        "an area without a radius",
                        ability("area", "", HEAL),
                        "/Abilities/x/radius",
                        "expected a number"),
                Arguments.of(
                        "a fractional cooldown",
                        ability("none", "\"cooldown\":1.5", HEAL),
                        "/Abilities/x/cooldown",
                        "whole number"),
                Arguments.of(
                        "a negative cooldown",
                        ability("none", "\"cooldown\":-3", HEAL),
                        "/Abilities/x/cooldown",
                        "whole number"),
                Arguments.of(
                        "a negative cost",
                        ability("none", "\"Cost\":{\"resources\":{\"food\":-1}}", HEAL),
                        "/Abilities/x/Cost/resources/food",
                        "expected an amount"),
                Arguments.of(
                        "a cost of the wrong type",
                        ability("none", "\"Cost\":{\"resources\":{\"food\":\"lots\"}}", HEAL),
                        "/Abilities/x/Cost/resources/food",
                        "expected an amount"),
                Arguments.of(
                        "an unknown cost key",
                        ability("none", "\"Cost\":{\"gold\":1}", HEAL),
                        "/Abilities/x/Cost/gold",
                        "unknown key"),
                Arguments.of(
                        "autocast without a pick",
                        ability("none", "\"autocast\":true", HEAL),
                        "/Abilities/x/pick",
                        "needs a pick"),
                Arguments.of(
                        "a pick without autocast",
                        ability("none", "\"pick\":\"self\"", HEAL),
                        "/Abilities/x/pick",
                        "for an autocast"),
                Arguments.of(
                        "an unknown picker",
                        ability("none", "\"autocast\":true,\"pick\":\"luckiest\"", HEAL),
                        "/Abilities/x/pick",
                        "expected one of"),
                Arguments.of(
                        "a picker that cannot aim the ability",
                        ability(
                                "point",
                                "\"autocast\":true,\"pick\":\"self\"",
                                "{\"kind\":\"teleport\",\"params\":{}}"),
                        "/Abilities/x/pick",
                        "cannot aim"),
                Arguments.of(
                        "autocast of the wrong type",
                        ability("none", "\"autocast\":\"yes\"", HEAL),
                        "/Abilities/x/autocast",
                        "true or false"),
                Arguments.of("no effects", ability("none", "", ""), "/Abilities/x/effects", "1 to 8"),
                Arguments.of(
                        "an effect that is no object",
                        ability("none", "", "5"),
                        "/Abilities/x/effects/0",
                        "expected {kind, params}"),
                Arguments.of(
                        "an unknown effect",
                        ability("none", "", "{\"kind\":\"smite\",\"params\":{}}"),
                        "/Abilities/x/effects/0/kind",
                        "expected one of"),
                Arguments.of(
                        "an unknown effect key",
                        ability("none", "", "{\"kind\":\"heal\",\"params\":{\"amount\":1},\"when\":3}"),
                        "/Abilities/x/effects/0/when",
                        "unknown key"),
                Arguments.of(
                        "params that are no object",
                        ability("none", "", "{\"kind\":\"heal\",\"params\":3}"),
                        "/Abilities/x/effects/0/params",
                        "expected an object"),
                Arguments.of(
                        "an effect that does not fit the target",
                        ability("none", "", "{\"kind\":\"damage\",\"params\":{\"type\":\"hack\",\"amount\":1}}"),
                        "/Abilities/x/effects/0/kind",
                        "does not fit a none"),
                Arguments.of(
                        "damage of an unknown type",
                        ability("unit", "", "{\"kind\":\"damage\",\"params\":{\"type\":\"fire\",\"amount\":1}}"),
                        "/Abilities/x/effects/0/params/type",
                        "unknown 'fire'"),
                Arguments.of(
                        "damage of nothing",
                        ability("unit", "", "{\"kind\":\"damage\",\"params\":{\"type\":\"hack\",\"amount\":0}}"),
                        "/Abilities/x/effects/0/params/amount",
                        "expected a number"),
                Arguments.of(
                        "a heal of the wrong type",
                        ability("none", "", "{\"kind\":\"heal\",\"params\":{\"amount\":\"all\"}}"),
                        "/Abilities/x/effects/0/params/amount",
                        "expected a number"),
                Arguments.of(
                        "modifiers for no ticks",
                        ability(
                                "none",
                                "",
                                modifiers("0", "{\"path\":\"UnitMotion/walkSpeed\",\"op\":\"mul\",\"value\":2}")),
                        "/Abilities/x/effects/0/params/ticks",
                        "whole number"),
                Arguments.of(
                        "modifiers for a day",
                        ability(
                                "none",
                                "",
                                modifiers(
                                        "99999999", "{\"path\":\"UnitMotion/walkSpeed\",\"op\":\"mul\",\"value\":2}")),
                        "/Abilities/x/effects/0/params/ticks",
                        "whole number"),
                Arguments.of(
                        "no modifiers",
                        ability("none", "", "{\"kind\":\"apply_modifiers\",\"params\":{\"ticks\":5,\"modifiers\":[]}}"),
                        "/Abilities/x/effects/0/params/modifiers",
                        "at least one"),
                Arguments.of(
                        "a modifier of an unknown op",
                        ability(
                                "none",
                                "",
                                modifiers("5", "{\"path\":\"UnitMotion/walkSpeed\",\"op\":\"pow\",\"value\":2}")),
                        "/Abilities/x/effects/0/params/modifiers/0/op",
                        "unknown 'pow'"),
                Arguments.of(
                        "a modifier of nothing in particular",
                        ability(
                                "none",
                                "",
                                modifiers("5", "{\"path\":\"UnitMotion/walkSpeed\",\"op\":\"mul\",\"value\":\"x\"}")),
                        "/Abilities/x/effects/0/params/modifiers/0/value",
                        "finite number"),
                Arguments.of(
                        "a modifier of a path no template has",
                        ability("none", "", modifiers("5", "{\"path\":\"Wings/span\",\"op\":\"add\",\"value\":2}")),
                        "/Abilities/x/effects/0/params/modifiers/0/path",
                        "no template has a number"),
                Arguments.of(
                        "a timed effect on nobody in particular",
                        ability(
                                "unit",
                                "",
                                "{\"kind\":\"capture_strength\",\"params\":{\"ticks\":5,\"amount\":1,\"on\":\"nobody\"}}"),
                        "/Abilities/x/effects/0/params/on",
                        "unknown 'nobody'"),
                Arguments.of(
                        "two timed effects",
                        ability(
                                "none",
                                "",
                                modifiers("5", "{\"path\":\"UnitMotion/walkSpeed\",\"op\":\"mul\",\"value\":2}") + ","
                                        + "{\"kind\":\"capture_strength\",\"params\":{\"ticks\":5,\"amount\":1}}"),
                        "/Abilities/x/effects/1/kind",
                        "at most one timed"),
                Arguments.of(
                        "a spawn of a template the set lacks",
                        ability("point", "", "{\"kind\":\"spawn\",\"params\":{\"template\":\"strategos:unit/ghost\"}}"),
                        "/Abilities/x/effects/0/params/template",
                        "not a unit template"),
                Arguments.of(
                        "a spawn of too many",
                        ability(
                                "point",
                                "",
                                "{\"kind\":\"spawn\",\"params\":{\"template\":\"strategos:unit/test\",\"count\":9}}"),
                        "/Abilities/x/effects/0/params/count",
                        "whole number"),
                Arguments.of(
                        "a spawn that names no template id",
                        ability("point", "", "{\"kind\":\"spawn\",\"params\":{\"template\":\"Ghost\"}}"),
                        "/Abilities/x/effects/0/params/template",
                        "not a template id"),
                Arguments.of(
                        "a teleport with parameters",
                        ability("point", "", "{\"kind\":\"teleport\",\"params\":{\"far\":1}}"),
                        "/Abilities/x/effects/0/params/far",
                        "unknown key"),
                Arguments.of("an id that is no id", "{\"Bad Id\":{}}", "/Abilities/Bad Id", "not an ability id"),
                Arguments.of("an ability that is no object", "{\"x\":4}", "/Abilities/x", "expected an object"),
                Arguments.of("abilities that are no object", "\"charge\"", "/Abilities", "expected an object"));
    }

    private static String modifiers(String ticks, String modifier) {
        return "{\"kind\":\"apply_modifiers\",\"params\":{\"ticks\":" + ticks + ",\"modifiers\":[" + modifier + "]}}";
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("defects")
    void aDefectIsAProblemAtItsPointerAndNeverAnException(String name, String abilities, String pointer, String part) {
        TemplateCompiler.Result result = compile(abilities);
        assertThat(result.ok()).isFalse();
        assertThat(result.problems())
                .filteredOn(p -> p.pointer().equals(pointer))
                .as("a problem at %s among %s", pointer, result.problems())
                .isNotEmpty()
                .anySatisfy(p -> assertThat(p.message()).contains(part));
        assertThat(result.problems()).allSatisfy(p -> assertThat(p.file()).isEqualTo("test.json"));
    }

    @Test
    void aBuildingTakesNoAbilities() {
        JsonObject json = JsonParser.parseString("{\"Identity\":{},\"Building\":{\"blueprint\":\"strategos:house\"},"
                        + "\"Abilities\":{\"x\":{}}}")
                .getAsJsonObject();
        TemplateCompiler.Result result = TemplateCompiler.compile(
                List.of(new RawTemplate(new TemplateId("strategos:building/b"), "b.json", json)));
        assertThat(result.problems()).extracting(TemplateProblem::pointer).contains("/Abilities");
    }

    @Test
    void anIdIsOneAbilityWhereverItAppearsSoTemplatesMustDeclareItAlike() {
        String same = ability("none", "", HEAL);
        String other = ability("none", "\"cooldown\":9", HEAL);
        TemplateCompiler.Result alike = TemplateCompiler.compile(
                List.of(raw("strategos:unit/a", "a.json", same), raw("strategos:unit/b", "b.json", same)));
        assertThat(alike.problems()).isEmpty();
        assertThat(alike.set().abilities()).hasSize(1);
        assertThat(alike.set()
                        .abilitiesOf(alike.set()
                                .byId(new TemplateId("strategos:unit/b"))
                                .netIndex()))
                .extracting(AbilityDef::id)
                .containsExactly("x");
        TemplateCompiler.Result differ = TemplateCompiler.compile(
                List.of(raw("strategos:unit/a", "a.json", same), raw("strategos:unit/b", "b.json", other)));
        assertThat(differ.problems()).singleElement().satisfies(p -> {
            assertThat(p.file()).isEqualTo("b.json");
            assertThat(p.pointer()).isEqualTo("/Abilities/x");
            assertThat(p.message()).contains("differs").contains("a.json");
        });
    }

    @Test
    void aWellFormedAbilityReadsBackAsWritten() {
        TemplateCompiler.Result result = compile("""
                {"charge": {"target":"area","radius":3.5,"range":8,"cooldown":600,
                            "Cost":{"resources":{"food":10,"wood":5.5}},
                            "effects":[{"kind":"apply_modifiers","params":{"ticks":100,"modifiers":[
                                {"path":"UnitMotion/walkSpeed","op":"mul","value":1.5}]}},
                               {"kind":"heal","params":{"amount":2}}]},
                 "zeal": {"target":"none","autocast":true,"pick":"self","cooldown":20,
                          "effects":[{"kind":"capture_strength","params":{"ticks":30,"amount":2}}]}}
                """);
        assertThat(result.problems()).isEmpty();
        TemplateSet set = result.set();
        assertThat(set.abilities()).extracting(AbilityDef::id).containsExactly("charge", "zeal");
        AbilityDef charge = set.ability("charge");
        assertThat(charge.aim()).isEqualTo(Aim.AREA);
        assertThat(charge.radius()).isEqualTo(3.5);
        assertThat(charge.range()).isEqualTo(8);
        assertThat(charge.cooldown()).isEqualTo(600);
        assertThat(charge.cost()).containsExactly(java.util.Map.entry("food", 10.0), java.util.Map.entry("wood", 5.5));
        assertThat(charge.autocast()).isFalse();
        assertThat(charge.effects()).extracting(AbilityDef.Effect::kind).containsExactly("apply_modifiers", "heal");
        assertThat(charge.timed().kind()).isEqualTo("apply_modifiers");
        assertThat(set.ability("zeal").pick()).isEqualTo("self");
        assertThat(set.hasAutocast()).isTrue();
        assertThat(set.abilityIndex("zeal")).isEqualTo(1);
        assertThat(set.abilityIndex("nope")).isEqualTo(-1);
        assertThat(set.version())
                .as("abilities are content: they are in the version")
                .isNotEqualTo(compile(null).set().version());
    }

    @Test
    void theBuiltInSetKeepsItsHorsemanAndAddsTheHealer() {
        TemplateSet builtin = TemplateSet.builtin();
        assertThat(builtin.abilities()).extracting(AbilityDef::id).containsExactly("charge", "heal");
        assertThat(builtin.abilitiesOf(
                        java.util.Objects.requireNonNull(builtin.byId(new TemplateId("strategos:unit/horseman")))
                                .netIndex()))
                .extracting(AbilityDef::id)
                .containsExactly("charge");
        assertThat(builtin.hasAutocast()).as("the healer heals by itself").isTrue();
        assertThat(builtin.abilitiesOf(0)).isEmpty();
        assertThat(builtin.abilitiesOf(-1)).isEmpty();
    }

    @Test
    void theVocabularyTheLintTeachesIsClosed() {
        assertThat(AbilityKinds.effects())
                .containsExactly("apply_modifiers", "capture_strength", "damage", "heal", "spawn", "teleport");
        assertThat(AbilityKinds.pickers()).containsExactly("most_wounded_ally", "nearest_enemy_in_range", "self");
    }
}
