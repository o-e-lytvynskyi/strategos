package dev.strategos.core.content;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.shared.TemplateId;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** UNIT-06 / UNIT-07: the horseman is the large mobility class on a mounted body, with the spec's starting values. */
class HorsemanTemplateTest {
    private static final TemplateSet SET = TemplateSet.builtin();
    private static final UnitTemplate HORSEMAN =
            Objects.requireNonNull(SET.byId(new TemplateId("strategos:unit/horseman")));

    @Test
    void horsemanIsLargeOnAMountedBody() {
        assertThat(HORSEMAN.netIndex()).as("appended after the archer").isEqualTo(17);
        assertThat(HORSEMAN.mobility()).isEqualTo("large");
        assertThat(HORSEMAN.bodyType()).isEqualTo("strategos:unit_mount");
        assertThat(HORSEMAN.width()).isEqualTo(1.4f);
        assertThat(HORSEMAN.height()).isEqualTo(2.0f);
        assertThat(HORSEMAN.radius()).isEqualTo(0.7);
        assertThat(HORSEMAN.walkSpeed()).isEqualTo(0.34);
        assertThat(HORSEMAN.classes()).contains("Cavalry", "Melee", "Unit");
    }

    @Test
    void horsemanHasTheApprovedStartingValues() {
        assertValue("Health/max", 200);
        assertValue("Attack/Melee/hack", 10);
        assertValue("Attack/Melee/reach", 1.5);
        assertValue("Attack/Melee/prepare", 8);
        assertValue("Attack/Melee/repeat", 18);
        assertValue("Resistance/hack", 3);
        assertValue("Resistance/pierce", 2);
        assertValue("Resistance/crush", 2);
        assertValue("Cost/resources/food", 80);
        assertValue("Cost/resources/wood", 40);
        assertValue("Cost/resources/iron", 30);
        assertValue("Cost/population", 1);
    }

    private static void assertValue(String path, double expected) {
        assertThat(SET.value(
                        HORSEMAN.netIndex(),
                        Objects.requireNonNull(SET.valuePaths().byPath(path)).id()))
                .as(path)
                .isEqualTo(expected);
    }
}
