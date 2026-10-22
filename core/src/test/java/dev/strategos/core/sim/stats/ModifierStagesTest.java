package dev.strategos.core.sim.stats;

import static dev.strategos.core.sim.stats.StatsFixture.HACK;
import static dev.strategos.core.sim.stats.StatsFixture.HEALTH;
import static dev.strategos.core.sim.stats.StatsFixture.template;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.content.CorePaths;
import dev.strategos.core.shared.FactionSlot;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * DATA-02.1 (spec §7.2): per stage ascending, the replacement of highest priority and then lowest source id, then the
 * products, then the sums, clamped to the path's declared range at the end of each stage; filters pick the templates; a
 * path a template lacks gets no value.
 */
class ModifierStagesTest {
    private static final FactionSlot F0 = new FactionSlot(0);
    private static final String SPEED = CorePaths.WALK_SPEED;
    private static final String TURN = CorePaths.TURN_RATE;

    private record Row(String name, String template, String path, List<Modifier> modifiers, double expected) {}

    private static final List<Row> ROWS = List.of(
            new Row("no modifier: the template's value", "spearman", HEALTH, List.of(), 100),
            new Row("a product", "spearman", HEALTH, List.of(Modifier.mul(HEALTH, 1.5, 1)), 150),
            new Row("a sum", "spearman", HEALTH, List.of(Modifier.add(HEALTH, 25, 1)), 125),
            new Row(
                    "replace, then multiply, then add, whatever the source ids",
                    "spearman",
                    HEALTH,
                    List.of(Modifier.add(HEALTH, 10, 1), Modifier.mul(HEALTH, 2, 2), Modifier.replace(HEALTH, 50, 3)),
                    110),
            new Row(
                    "a later stage multiplies the sum of an earlier one",
                    "spearman",
                    HEALTH,
                    List.of(Modifier.mul(HEALTH, 2, 1).atStage(1), Modifier.add(HEALTH, 10, 2)),
                    220),
            new Row(
                    "the replacement of higher priority wins",
                    "spearman",
                    HEALTH,
                    List.of(
                            Modifier.replace(HEALTH, 50, 1).withPriority(1),
                            Modifier.replace(HEALTH, 70, 2).withPriority(2)),
                    70),
            new Row(
                    "on equal priority the lower source id wins",
                    "spearman",
                    HEALTH,
                    List.of(Modifier.replace(HEALTH, 50, 5), Modifier.replace(HEALTH, 70, 3)),
                    70),
            new Row(
                    "a later stage's replacement drops the earlier stages' work",
                    "spearman",
                    HEALTH,
                    List.of(
                            Modifier.mul(HEALTH, 3, 1),
                            Modifier.replace(HEALTH, 40, 2).atStage(2)),
                    40),
            new Row(
                    "products and sums together",
                    "spearman",
                    HEALTH,
                    List.of(
                            Modifier.mul(HEALTH, 2, 1),
                            Modifier.mul(HEALTH, 0.5, 2),
                            Modifier.add(HEALTH, 3, 3),
                            Modifier.add(HEALTH, -1, 4)),
                    102),
            new Row("speed clamps to a block a tick", "spearman", SPEED, List.of(Modifier.mul(SPEED, 10, 1)), 1),
            new Row("speed clamps to 0", "spearman", SPEED, List.of(Modifier.add(SPEED, -1, 1)), 0),
            new Row("turn rate clamps to half a turn", "horseman", TURN, List.of(Modifier.add(TURN, 500, 1)), 180),
            new Row(
                    "each stage ends clamped",
                    "spearman",
                    SPEED,
                    List.of(
                            Modifier.add(SPEED, -1, 1),
                            Modifier.add(SPEED, 0.5, 2).atStage(1)),
                    0.5),
            new Row(
                    "a filter the template fails changes nothing",
                    "spearman",
                    HEALTH,
                    List.of(Modifier.mul(HEALTH, 2, 1).withFilter("Cavalry")),
                    100),
            new Row(
                    "clauses are OR-ed",
                    "horseman",
                    HEALTH,
                    List.of(Modifier.mul(HEALTH, 2, 1).withFilter("Ranged Cavalry")),
                    320),
            new Row(
                    "a negated token excludes",
                    "champion",
                    HEALTH,
                    List.of(Modifier.mul(HEALTH, 2, 1).withFilter("Infantry+Melee+!Champion")),
                    250),
            new Row(
                    "every token of a clause must hold",
                    "spearman",
                    HEALTH,
                    List.of(Modifier.mul(HEALTH, 2, 1).withFilter("Infantry+Melee+!Champion")),
                    200),
            new Row(
                    "a clause naming a token no template has matches nothing",
                    "spearman",
                    HEALTH,
                    List.of(Modifier.mul(HEALTH, 2, 1).withFilter("Infantry+Elephant")),
                    100),
            new Row(
                    "a path the template lacks stays without a value",
                    "archer",
                    HACK,
                    List.of(Modifier.add(HACK, 5, 1), Modifier.replace(HACK, 9, 2)),
                    Double.NaN),
            new Row(
                    "a path no template has changes nothing",
                    "spearman",
                    HEALTH,
                    List.of(Modifier.add("Nothing/here", 5, 1)),
                    100));

    @Test
    void eachRowEvaluatesToItsValue() {
        for (Row row : ROWS) {
            StatService stats = StatsFixture.service();
            for (Modifier m : row.modifiers()) {
                stats.add(F0, m);
            }

            double value = stats.blockValue(template(row.template()), 0, stats.path(row.path()));

            if (Double.isNaN(row.expected())) {
                assertThat(value).as(row.name()).isNaN();
            } else {
                assertThat(value).as(row.name()).isEqualTo(row.expected());
            }
        }
    }

    @Test
    void theOtherFactionKeepsTheTemplatesValue() {
        StatService stats = StatsFixture.service();
        stats.add(F0, Modifier.mul(HEALTH, 2, 1));

        assertThat(stats.blockValue(template("spearman"), 1, stats.path(HEALTH)))
                .isEqualTo(100);
    }

    @Test
    void malformedModifiersAreRefused() {
        assertThatThrownBy(() -> Modifier.mul(HEALTH, Double.NaN, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Modifier.add(HEALTH, 1, 1).atStage(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Modifier.add(HEALTH, 1, 1).withFilter("Infantry++Melee"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Modifier.add(HEALTH, 1, 1).withFilter("!"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Modifier.add(" ", 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StatsFixture.service().add(new FactionSlot(2), Modifier.add(HEALTH, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
