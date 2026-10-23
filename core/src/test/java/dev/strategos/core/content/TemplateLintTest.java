package dev.strategos.core.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonParser;
import dev.strategos.core.shared.TemplateId;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * DATA-03.2 (spec §5.3): an unknown key in a component the core decodes is an error naming the file and the key's JSON
 * pointer, even when a parent brings it in; a component no M1 card decodes passes, its numbers being value paths.
 */
class TemplateLintTest {

    @Test
    void unknownKeysAreReportedWithFileAndPointer() {
        RawTemplate base = raw("test:template/base", "test/template/base.json", """
                {
                  "abstract": true,
                  "UnitMotion": { "walkSpeed": 0.25, "walkspeed": 0.3 },
                  "Body": { "entityType": "strategos:unit_humanoid", "width": 0.6, "height": 1.8 }
                }
                """);
        RawTemplate unit = raw("test:unit/scout", "test/unit/scout.json", """
                {
                  "parent": "test:template/base",
                  "Identity": { "name": "scout", "colour": "red" },
                  "Attack": { "damage": 4 }
                }
                """);

        TemplateCompiler.Result result = TemplateCompiler.compile(List.of(base, unit));

        assertThat(result.ok()).isFalse();
        assertThat(result.problems())
                .extracting(TemplateProblem::file, TemplateProblem::pointer)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("test/unit/scout.json", "/Identity/colour"),
                        org.assertj.core.groups.Tuple.tuple("test/unit/scout.json", "/UnitMotion/walkspeed"));
        assertThat(result.problems()).allSatisfy(p -> assertThat(p.message()).startsWith("unknown key"));
    }

    private static RawTemplate raw(String id, String file, String json) {
        return new RawTemplate(
                new TemplateId(id), file, JsonParser.parseString(json).getAsJsonObject());
    }
}
