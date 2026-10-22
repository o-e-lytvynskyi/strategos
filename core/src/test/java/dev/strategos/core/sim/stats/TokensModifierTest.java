package dev.strategos.core.sim.stats;

import static dev.strategos.core.sim.stats.StatsFixture.HEALTH;
import static dev.strategos.core.sim.stats.StatsFixture.UNITS;
import static dev.strategos.core.sim.stats.StatsFixture.template;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.shared.FactionSlot;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * DATA-02.3 (spec §7.2): {@code TOKENS} edits a production list: {@code +a} appends a token the list lacks, {@code -b}
 * removes one, by stage and then source id; a template without the list gets none.
 */
class TokensModifierTest {
    private static final FactionSlot F0 = new FactionSlot(0);
    private static final String SPEARMAN = "strategos:unit/spearman";
    private static final String ARCHER = "strategos:unit/archer";
    private static final String CHAMPION = "strategos:unit/champion";

    @Test
    void plusAddsAndMinusRemoves() {
        StatService stats = StatsFixture.service();

        stats.add(F0, Modifier.tokens(UNITS, "+" + CHAMPION + " -" + ARCHER, 1));

        assertThat(units(stats, 0)).containsExactly(SPEARMAN, CHAMPION);
        assertThat(units(stats, 1)).as("the other faction's list").containsExactly(SPEARMAN, ARCHER);
    }

    @Test
    void addingAPresentTokenOrRemovingAMissingOneChangesNothing() {
        StatService stats = StatsFixture.service();

        stats.add(F0, Modifier.tokens(UNITS, "+" + SPEARMAN + " -" + CHAMPION, 1));

        assertThat(units(stats, 0)).containsExactly(SPEARMAN, ARCHER);
    }

    @Test
    void editsApplyBySourceIdThenStage() {
        StatService stats = StatsFixture.service();
        stats.add(F0, Modifier.tokens(UNITS, "-" + CHAMPION, 2));
        stats.add(F0, Modifier.tokens(UNITS, "+" + CHAMPION, 1));
        assertThat(units(stats, 0)).as("source 1 adds, source 2 removes").containsExactly(SPEARMAN, ARCHER);

        stats.add(F0, Modifier.tokens(UNITS, "+" + CHAMPION, 3).atStage(1));
        assertThat(units(stats, 0)).as("a later stage adds it back").containsExactly(SPEARMAN, ARCHER, CHAMPION);
    }

    @Test
    void aTemplateWithoutTheListGetsNone() {
        StatService stats = StatsFixture.service();

        stats.add(F0, Modifier.tokens(UNITS, "+" + CHAMPION, 1));

        assertThat(stats.blockTokens(template("spearman"), 0, stats.tokenPath(UNITS)))
                .isNull();
    }

    @Test
    void numbersAndListsDoNotMix() {
        StatService stats = StatsFixture.service();

        stats.add(F0, Modifier.tokens(HEALTH, "+a", 1));
        stats.add(F0, Modifier.add(UNITS, 5, 2));

        assertThat(stats.blockValue(template("captain"), 0, stats.path(HEALTH))).isEqualTo(100);
        assertThat(units(stats, 0)).containsExactly(SPEARMAN, ARCHER);
        assertThatThrownBy(() -> Modifier.tokens(UNITS, "a", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Modifier.tokens(UNITS, "+", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Modifier.tokens(UNITS, " ", 1)).isInstanceOf(IllegalArgumentException.class);
    }

    private static List<String> units(StatService stats, int faction) {
        List<String> list = stats.blockTokens(template("captain"), faction, stats.tokenPath(UNITS));
        assertThat(list).isNotNull();
        return list;
    }
}
