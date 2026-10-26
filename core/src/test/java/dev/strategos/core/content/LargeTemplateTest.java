package dev.strategos.core.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.shared.TemplateId;
import java.util.List;
import org.junit.jupiter.api.Test;

class LargeTemplateTest {
    @Test
    void largeMobilityDefaultsToItsWiderAvoidanceRadius() {
        TemplateCompiler.Result result = compile(large());
        assertThat(result.problems()).isEmpty();
        assertThat(result.set().byIndex(0).mobility()).isEqualTo("large");
        assertThat(result.set().byIndex(0).radius()).isEqualTo(0.7);
    }

    @Test
    void aLargeTemplateCannotDeclareAnInfantrySizedFootprint() {
        JsonObject json = large();
        json.getAsJsonObject("Body").addProperty("radius", 0.35);
        assertThat(compile(json).problems())
                .extracting(TemplateProblem::toString)
                .containsExactly("large.json: /Body/radius: large mobility needs a radius of at least 0.7");
    }

    @Test
    void anUnknownMobilityIsStillRejected() {
        JsonObject json = large();
        json.getAsJsonObject("UnitMotion").addProperty("mobility", "tiny");
        assertThat(compile(json).problems())
                .extracting(TemplateProblem::toString)
                .containsExactly(
                        "large.json: /UnitMotion/mobility: unknown mobility 'tiny'; expected infantry or large");
    }

    private static JsonObject large() {
        return JsonParser.parseString("""
                {"Identity":{},"UnitMotion":{"walkSpeed":0.25,"mobility":"large"},
                 "Body":{"entityType":"strategos:unit_humanoid","width":1.4,"height":2}}
                """).getAsJsonObject();
    }

    private static TemplateCompiler.Result compile(JsonObject json) {
        return TemplateCompiler.compile(
                List.of(new RawTemplate(new TemplateId("strategos:unit/large"), "large.json", json)));
    }
}
