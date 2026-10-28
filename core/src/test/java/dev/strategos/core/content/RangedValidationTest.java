package dev.strategos.core.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.shared.TemplateId;
import java.util.List;
import org.junit.jupiter.api.Test;

class RangedValidationTest {
    @Test
    void malformedRangedValuesAreLoadErrorsAtTheirPointers() {
        Object[][] invalid = {
            {"hack", -1}, {"hack", 1e39}, {"pierce", Double.NaN}, {"pierce", 1e39},
            {"crush", "bad"}, {"crush", 1e39}, {"fire", -0.1}, {"fire", 1e39},
            {"range", 129}, {"prepare", -1}, {"repeat", 0}, {"repeat", 201},
            {"speed", 0}, {"speed", 65}, {"spread", -1}, {"spread", 101}
        };
        for (Object[] row : invalid) {
            JsonObject ranged = new JsonObject();
            Object value = row[1];
            if (value instanceof Number number) ranged.addProperty((String) row[0], number);
            else ranged.addProperty((String) row[0], (String) value);
            TemplateCompiler.Result result = compile(ranged);
            assertThat(result.ok()).as(row[0] + "=" + value).isFalse();
            assertThat(result.problems()).extracting(TemplateProblem::pointer).contains("/Attack/Ranged/" + row[0]);
        }
    }

    @Test
    void zeroDamageRangePrepareAndSpreadAreValidButSpeedIsPositive() {
        JsonObject ranged = JsonParser.parseString("""
                {"hack":0,"pierce":0,"crush":0,"fire":0,"range":0,"prepare":0,
                 "repeat":1,"speed":0.01,"spread":0}
                """).getAsJsonObject();
        assertThat(compile(ranged).ok()).isTrue();
    }

    @Test
    void highGroundKnobsAreValidatedAtTheirPointers() {
        Object[][] invalid = {
            {"rangePerBlock", -0.1},
            {"rangePerBlock", 1.5},
            {"damagePerBlock", -1},
            {"damagePerBlock", 2},
            {"maxBlocks", -1},
            {"maxBlocks", 65}
        };
        for (Object[] row : invalid) {
            JsonObject high = new JsonObject();
            high.addProperty((String) row[0], (Number) row[1]);
            JsonObject ranged = new JsonObject();
            ranged.add("highGround", high);
            TemplateCompiler.Result result = compile(ranged);
            assertThat(result.ok()).as(row[0] + "=" + row[1]).isFalse();
            assertThat(result.problems())
                    .extracting(TemplateProblem::pointer)
                    .contains("/Attack/Ranged/highGround/" + row[0]);
        }
        JsonObject ok = JsonParser.parseString(
                        "{\"highGround\":{\"rangePerBlock\":0,\"damagePerBlock\":1,\"maxBlocks\":64}}")
                .getAsJsonObject();
        assertThat(compile(ok).ok()).isTrue();
    }

    private static TemplateCompiler.Result compile(JsonObject ranged) {
        JsonObject json = JsonParser.parseString("""
                {"Identity":{},"UnitMotion":{"walkSpeed":0.25},
                 "Body":{"entityType":"strategos:unit_humanoid","width":0.6,"height":1.8}}
                """).getAsJsonObject();
        JsonObject attack = new JsonObject();
        attack.add("Ranged", ranged);
        json.add("Attack", attack);
        return TemplateCompiler.compile(
                List.of(new RawTemplate(new TemplateId("strategos:unit/test"), "test.json", json)));
    }
}
