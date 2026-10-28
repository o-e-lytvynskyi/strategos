package dev.strategos.core.content;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.shared.TemplateId;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class ArcherTemplateTest {
    @Test
    void archerHasTheApprovedStartingValues() {
        TemplateSet set = TemplateSet.builtin();
        UnitTemplate archer = Objects.requireNonNull(set.byId(new TemplateId("strategos:unit/archer")));
        assertThat(archer.walkSpeed()).isEqualTo(0.2);
        assertThat(archer.classes()).contains("Archer", "Infantry", "Organic");
        assertValue(set, archer, "Health/max", 70);
        assertValue(set, archer, "Attack/Ranged/pierce", 16);
        assertValue(set, archer, "Attack/Ranged/range", 20);
        assertValue(set, archer, "Attack/Ranged/prepare", 16);
        assertValue(set, archer, "Attack/Ranged/repeat", 20);
        assertValue(set, archer, "Attack/Ranged/speed", 4);
        assertValue(set, archer, "Attack/Ranged/spread", 2.25);
        assertValue(set, archer, "Vision/range", 32);
        assertValue(set, archer, "Resistance/hack", 1);
        assertValue(set, archer, "Resistance/pierce", 1);
        assertValue(set, archer, "Resistance/crush", 1);
        assertValue(set, archer, "Cost/resources/food", 60);
        assertValue(set, archer, "Cost/resources/wood", 40);
        assertValue(set, archer, "Cost/resources/iron", 20);
        assertValue(set, archer, "Cost/population", 1);
        assertValue(set, archer, "Cost/time", 15);
    }

    @Test
    void existingTemplateIndicesStayFixedWhenLaterUnitsAreAppended() {
        assertThat(TemplateSet.builtin().all())
                .extracting(t -> t.id().value())
                .containsExactly(
                        "strategos:unit/debug_walker",
                        "strategos:unit/infantry",
                        "strategos:unit/worker",
                        "strategos:building/barracks",
                        "strategos:building/civic_centre",
                        "strategos:building/field",
                        "strategos:building/house",
                        "strategos:building/iron_mine",
                        "strategos:building/iron_mine_large",
                        "strategos:building/quarry",
                        "strategos:building/quarry_large",
                        "strategos:unit/chicken",
                        "strategos:unit/pig",
                        "strategos:unit/sheep",
                        "strategos:building/farmstead",
                        "strategos:building/storehouse",
                        "strategos:unit/archer",
                        "strategos:unit/horseman");
    }

    private static void assertValue(TemplateSet set, UnitTemplate template, String path, double expected) {
        assertThat(set.value(
                        template.netIndex(),
                        Objects.requireNonNull(set.valuePaths().byPath(path)).id()))
                .as(path)
                .isEqualTo(expected);
    }
}
