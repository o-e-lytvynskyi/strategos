package dev.strategos.core.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonParser;
import dev.strategos.core.shared.TemplateId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** DATA-01.3: cycles, unknown parents and bad fields fail with file, pointer and reason, every one of them. */
class TemplateProblemsTest {
    private static final String GOOD_UNIT = """
            {"Identity": {}, "UnitMotion": {"walkSpeed": 0.25}, "Body": {"entityType": "strategos:unit_humanoid",
            "width": 0.6, "height": 1.8}}
            """;

    @Test
    void aCycleNamesItsWholeChain() {
        TemplateCompiler.Result result = TemplateCompiler.compile(List.of(
                raw("strategos:x/a", "{\"parent\": \"strategos:x/b\"}"),
                raw("strategos:x/b", "{\"parent\": \"strategos:x/a\"}")));

        assertThat(result.problems())
                .extracting(TemplateProblem::toString)
                .containsExactly("x/a.json: /parent: inheritance cycle strategos:x/a → strategos:x/b → strategos:x/a");
        assertThat(result.set().all()).isEmpty();
    }

    @Test
    void everyProblemIsReportedNotOnlyTheFirst() {
        List<RawTemplate> raws = new ArrayList<>();
        raws.add(raw("strategos:x/a", "{\"parent\": \"strategos:x/b\"}"));
        raws.add(raw("strategos:x/b", "{\"parent\": \"strategos:x/a\"}"));
        raws.add(raw("strategos:x/child_of_a", "{\"parent\": \"strategos:x/a\"}"));
        raws.add(raw("strategos:x/orphan", "{\"parent\": \"strategos:x/missing\"}"));
        raws.add(raw("strategos:x/no_mixin", "{\"mixins\": [\"strategos:x/nope\"]}"));
        raws.add(raw("strategos:x/slow", GOOD_UNIT.replace("0.25", "\"fast\"")));
        raws.add(raw("strategos:x/bodiless", "{\"Identity\": {}, \"UnitMotion\": {\"walkSpeed\": 0.25}}"));
        raws.add(raw("strategos:x/good", GOOD_UNIT));

        TemplateCompiler.Result result = TemplateCompiler.compile(raws);

        assertThat(result.problems())
                .extracting(TemplateProblem::toString)
                .containsExactlyInAnyOrder(
                        "x/a.json: /parent: inheritance cycle strategos:x/a → strategos:x/b → strategos:x/a",
                        "x/child_of_a.json: /parent: its parent 'strategos:x/a' does not compile",
                        "x/orphan.json: /parent: unknown parent 'strategos:x/missing' in strategos:x/orphan → strategos:x/missing",
                        "x/no_mixin.json: /mixins/0: unknown mixin 'strategos:x/nope' in strategos:x/no_mixin → strategos:x/nope",
                        "x/slow.json: /UnitMotion/walkSpeed: expected a number, got \"fast\"",
                        "x/bodiless.json: /Body: missing component Body");
        assertThat(result.ok()).isFalse();
        assertThat(result.set().all()).extracting(UnitTemplate::id).containsExactly(new TemplateId("strategos:x/good"));
    }

    @Test
    void aDuplicateIdIsAProblem() {
        TemplateCompiler.Result result = TemplateCompiler.compile(List.of(
                new RawTemplate(
                        new TemplateId("strategos:x/good"),
                        "one.json",
                        JsonParser.parseString(GOOD_UNIT).getAsJsonObject()),
                new RawTemplate(
                        new TemplateId("strategos:x/good"),
                        "two.json",
                        JsonParser.parseString(GOOD_UNIT).getAsJsonObject())));

        assertThat(result.problems())
                .extracting(TemplateProblem::toString)
                .containsExactly("two.json: /: template strategos:x/good is also defined in one.json");
    }

    private static RawTemplate raw(String id, String json) {
        return new RawTemplate(
                new TemplateId(id),
                id.substring(id.indexOf(':') + 1) + ".json",
                JsonParser.parseString(json).getAsJsonObject());
    }
}
