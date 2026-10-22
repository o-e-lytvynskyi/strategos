package dev.strategos.core.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/** DATA-01.4: each derived kind keeps or overrides exactly its parts. */
class TemplateFilterTest {
    private static final JsonObject BARRACKS = JsonParser.parseString("""
            {"Identity": {"name": "barracks"}, "Footprint": {"width": 7}, "Cost": {"wood": 300},
             "Obstruction": {"solid": true}, "Health": {"max": 2000}, "Attack": {"Ranged": {}},
             "Vision": {"range": 24}, "Body": {"structure": "strategos:barracks"}, "Production": {"units": "+spearman"}}
            """).getAsJsonObject();

    @Test
    void aFoundationKeepsIdentityFootprintCostObstructionAndProgressHealth() {
        JsonObject foundation = TemplateFilter.FOUNDATION.apply(BARRACKS);

        assertThat(foundation.keySet()).containsExactly("Identity", "Footprint", "Cost", "Obstruction", "Health");
        assertThat(foundation.getAsJsonObject("Health").get("progressDriven").getAsBoolean())
                .isTrue();
        assertThat(foundation.getAsJsonObject("Health").get("max").getAsInt()).isEqualTo(2000);
        assertThat(BARRACKS.getAsJsonObject("Health").has("progressDriven"))
                .as("the original is untouched")
                .isFalse();
    }

    @Test
    void aPreviewKeepsIdentityFootprintAndBody() {
        assertThat(TemplateFilter.PREVIEW.apply(BARRACKS).keySet()).containsExactly("Identity", "Footprint", "Body");
    }

    @Test
    void aMirageKeepsWhatAnEnemyLastSaw() {
        JsonObject mirage = TemplateFilter.MIRAGE.apply(BARRACKS);

        assertThat(mirage.keySet()).containsExactly("Identity", "Footprint", "Health", "Body");
        assertThat(mirage.getAsJsonObject("Health").has("progressDriven")).isFalse();
    }
}
