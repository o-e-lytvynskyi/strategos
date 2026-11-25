package dev.strategos.core.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.shared.TemplateId;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * CMB-11.3: {@code Promotion} is linted at compile time like the other kinds: every defect is a {@link TemplateProblem}
 * at the JSON pointer of the field, never an exception, whatever the shape of the data; what compiles reads back as
 * written, in milli-points, and the built-in set gives ranks to soldiers and to nobody else.
 */
class PromotionLintTest {
    private static final String UNIT = """
            {"Identity":{},"UnitMotion":{"walkSpeed":0.25},"Capture":{"strength":2},"Health":{"max":50},
             "Body":{"entityType":"strategos:unit_humanoid","width":0.6,"height":1.8}}
            """;

    private static final String MOD = "{\"path\":\"Health/max\",\"op\":\"mul\",\"value\":1.1}";

    private static TemplateCompiler.Result compile(String promotion) {
        JsonObject json = JsonParser.parseString(UNIT).getAsJsonObject();
        json.add("Promotion", JsonParser.parseString(promotion));
        return TemplateCompiler.compile(
                List.of(new RawTemplate(new TemplateId("strategos:unit/test"), "test.json", json)));
    }

    static Stream<Arguments> defects() {
        return Stream.of(
                Arguments.of("a promotion that is no object", "7", "/Promotion", "expected an object"),
                Arguments.of("an unknown key", "{\"medals\":1}", "/Promotion/medals", "unknown key"),
                Arguments.of("a negative value", "{\"value\":-1}", "/Promotion/value", "expected a number"),
                Arguments.of(
                        "a value of the wrong type", "{\"value\":\"lots\"}", "/Promotion/value", "expected a number"),
                Arguments.of("a value past the cap", "{\"value\":1e9}", "/Promotion/value", "expected a number"),
                Arguments.of(
                        "thresholds that are no list",
                        "{\"thresholds\":5}",
                        "/Promotion/thresholds",
                        "expected a list of one to 8"),
                Arguments.of("no thresholds", "{\"thresholds\":[]}", "/Promotion/thresholds", "expected a list of one"),
                Arguments.of(
                        "too many thresholds",
                        "{\"thresholds\":[1,2,3,4,5,6,7,8,9]}",
                        "/Promotion/thresholds",
                        "expected a list of one"),
                Arguments.of(
                        "a threshold of the wrong type",
                        "{\"thresholds\":[10,\"x\"]}",
                        "/Promotion/thresholds/1",
                        "expected a number"),
                Arguments.of(
                        "thresholds that do not ascend",
                        "{\"thresholds\":[10,10]}",
                        "/Promotion/thresholds/1",
                        "above the one before"),
                Arguments.of(
                        "a threshold of nothing",
                        "{\"thresholds\":[0]}",
                        "/Promotion/thresholds/0",
                        "above the one before"),
                Arguments.of("ranks without thresholds", "{\"ranks\":[[]]}", "/Promotion/ranks", "need thresholds"),
                Arguments.of(
                        "ranks that are no list",
                        "{\"thresholds\":[10],\"ranks\":{}}",
                        "/Promotion/ranks",
                        "expected a list"),
                Arguments.of(
                        "a rank per threshold",
                        "{\"thresholds\":[10,20],\"ranks\":[[" + MOD + "]]}",
                        "/Promotion/ranks",
                        "expected 2 ranks"),
                Arguments.of(
                        "a rank that is no list",
                        "{\"thresholds\":[10],\"ranks\":[3]}",
                        "/Promotion/ranks/0",
                        "expected a list of modifiers"),
                Arguments.of(
                        "a modifier of an unknown op",
                        "{\"thresholds\":[10],\"ranks\":[[{\"path\":\"Health/max\",\"op\":\"pow\",\"value\":2}]]}",
                        "/Promotion/ranks/0/0/op",
                        "unknown 'pow'"),
                Arguments.of(
                        "a modifier of nothing in particular",
                        "{\"thresholds\":[10],\"ranks\":[[{\"path\":\"Health/max\",\"op\":\"mul\",\"value\":\"x\"}]]}",
                        "/Promotion/ranks/0/0/value",
                        "finite number"),
                Arguments.of(
                        "a modifier of a path no template has",
                        "{\"thresholds\":[10],\"ranks\":[[{\"path\":\"Wings/span\",\"op\":\"add\",\"value\":2}]]}",
                        "/Promotion/ranks/0/0/path",
                        "no template has a number"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("defects")
    void aDefectIsAProblemAtItsPointerAndNeverAnException(String name, String promotion, String pointer, String part) {
        TemplateCompiler.Result result = compile(promotion);
        assertThat(result.ok()).isFalse();
        assertThat(result.problems())
                .filteredOn(p -> p.pointer().equals(pointer))
                .as("a problem at %s among %s", pointer, result.problems())
                .isNotEmpty()
                .anySatisfy(p -> assertThat(p.message()).contains(part));
        assertThat(result.problems()).allSatisfy(p -> assertThat(p.file()).isEqualTo("test.json"));
    }

    @Test
    void aBuildingMayHaveAValueButNoRanks() {
        JsonObject json = JsonParser.parseString("{\"Identity\":{},\"Building\":{\"blueprint\":\"strategos:house\"},"
                        + "\"Promotion\":{\"value\":50,\"thresholds\":[10]}}")
                .getAsJsonObject();
        TemplateCompiler.Result result = TemplateCompiler.compile(
                List.of(new RawTemplate(new TemplateId("strategos:building/b"), "b.json", json)));
        assertThat(result.problems()).extracting(TemplateProblem::pointer).contains("/Promotion");
    }

    @Test
    void whatCompilesReadsBackInMilliPointsAndRanksMayBeInsigniaOnly() {
        TemplateCompiler.Result result = compile("{\"value\":40,\"thresholds\":[2.5,10],\"ranks\":[[" + MOD + "],[]]}");
        assertThat(result.problems()).isEmpty();
        PromotionDef def = result.set().promotion(0);
        assertThat(def).isNotNull();
        assertThat(def.thresholds()).containsExactly(2_500L, 10_000L);
        assertThat(def.ranks().get(0)).containsExactly(new PromotionDef.Mod("Health/max", "mul", 1.1));
        assertThat(def.ranks().get(1)).isEmpty();
        TemplateCompiler.Result bare = compile("{\"thresholds\":[10]}");
        assertThat(bare.problems()).isEmpty();
        assertThat(bare.set().promotion(0).ranks()).containsExactly(List.of());
        TemplateCompiler.Result valueOnly = compile("{\"value\":5}");
        assertThat(valueOnly.problems()).isEmpty();
        assertThat(valueOnly.set().promotion(0)).as("a value alone is no rank").isNull();
    }

    @Test
    void theBuiltinSetGivesRanksToSoldiersAndToNobodyElse() {
        TemplateSet set = TemplateSet.builtin();
        for (UnitTemplate t : set.all()) {
            boolean soldier = t.id().value().matches("strategos:unit/(infantry|archer|horseman)");
            assertThat(set.promotion(t.netIndex()) != null).as(t.id().value()).isEqualTo(soldier);
        }
        PromotionDef def = set.promotion(set.byId(TemplateSet.INFANTRY).netIndex());
        assertThat(def.thresholds()).containsExactly(200_000L, 500_000L, 1_000_000L);
        assertThat(def.ranks()).hasSize(3).allSatisfy(rank -> assertThat(rank).isNotEmpty());
    }
}
