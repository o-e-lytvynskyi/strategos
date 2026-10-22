package dev.strategos.core.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonParser;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.testkit.TestTemplates;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** DATA-01.1: a three-level chain with two mixins resolves to the golden flattened template. */
class TemplateCompilerTest {
    private static final TemplateId SPEARMAN = new TemplateId("strategos:unit/spearman");

    @Test
    void chainWithTwoMixins() throws IOException {
        TemplateCompiler.Result result = TestTemplates.compile("templates/chain");

        assertThat(result.problems()).isEmpty();
        String golden;
        try (InputStream in = getClass().getResourceAsStream("/golden/spearman_flat.json")) {
            assertThat(in).isNotNull();
            golden = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(CanonicalJson.write(result.flattened().get(SPEARMAN)))
                .isEqualTo(CanonicalJson.write(JsonParser.parseString(golden)));
    }

    @Test
    void onlyConcreteTemplatesEnterTheSet() {
        TemplateSet set = TestTemplates.compile("templates/chain").set();

        assertThat(set.all()).extracting(UnitTemplate::id).containsExactly(SPEARMAN);
        UnitTemplate spearman = set.byIndex(0);
        assertThat(spearman.classes()).containsExactly("Builder", "Gatherer", "Infantry", "Melee", "Spearman", "Unit");
        assertThat(spearman.walkSpeed()).isEqualTo(0.2);
        assertThat(spearman.name()).isEqualTo("strategos.unit.spearman");
        assertThat(set.flattened(SPEARMAN)).isNotNull();
        assertThat(set.valuePaths().byPath("Health/max")).isNotNull();
        assertThat(set.valuePaths().byPath("Identity/classes"))
                .as("strings are no value paths")
                .isNull();
    }

    @Test
    void equalContentGivesAnEqualVersion() {
        assertThat(TestTemplates.compile("templates/chain").set().version())
                .isEqualTo(TestTemplates.compile("templates/chain").set().version());
    }
}
