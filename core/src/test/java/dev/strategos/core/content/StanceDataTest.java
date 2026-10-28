package dev.strategos.core.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.shared.Stance;
import dev.strategos.core.shared.TemplateId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** CMB-05's data: {@code Behaviour/stance} by name becomes the numeric path; a name no stance has is an error. */
class StanceDataTest {
    private static final TemplateId ID = new TemplateId("strategos:unit/guard");
    private static final String BODY = """
            {"Identity":{},"UnitMotion":{"walkSpeed":0.25},
             "Body":{"entityType":"strategos:unit_humanoid","width":0.6,"height":1.8}%s}
            """;

    private static TemplateCompiler.Result compile(String extra) {
        JsonObject json = JsonParser.parseString(BODY.formatted(extra)).getAsJsonObject();
        return TemplateCompiler.compile(List.of(new RawTemplate(ID, "guard.json", json)));
    }

    @Test
    void aStanceByNameIsTheNumberOfItsPath() {
        for (Stance stance : Stance.values()) {
            TemplateCompiler.Result result = compile(",\"Behaviour\":{\"stance\":\"" + stance.id() + "\"}");
            assertThat(result.ok()).as(result.problems().toString()).isTrue();
            TemplateSet set = result.set();
            ValuePath path = set.valuePaths().byPath(CorePaths.STANCE);
            assertThat(path).isNotNull();
            assertThat(set.value(0, path.id())).isEqualTo(stance.code());
        }
    }

    @Test
    void aTemplateThatSaysNothingIsAggressive() {
        TemplateSet set = compile("").set();
        assertThat(set.value(0, set.valuePaths().byPath(CorePaths.STANCE).id())).isEqualTo(Stance.AGGRESSIVE.code());
    }

    @Test
    void aNameNoStanceHasIsAnErrorAtItsPointer() {
        TemplateCompiler.Result result = compile(",\"Behaviour\":{\"stance\":\"hold\"}");
        assertThat(result.ok()).isFalse();
        assertThat(result.problems())
                .anyMatch(p ->
                        p.pointer().equals("/Behaviour/stance") && p.message().contains("hold"));
    }

    @Test
    void aSetRebuiltFromItsFlattenedTemplatesKeepsTheStance() {
        TemplateCompiler.Result result = compile(",\"Behaviour\":{\"stance\":\"defensive\"}");
        TemplateSet set = result.set();
        TemplateCompiler.Result again =
                TemplateCompiler.fromFlattened(List.of(new TemplateCompiler.Flat(ID, 0, set.flattened(ID))));
        assertThat(again.ok()).isTrue();
        assertThat(again.set().hash()).isEqualTo(set.hash());
    }
}
