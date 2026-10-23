package dev.strategos.core.client.look;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.client.hud.HudTheme;
import dev.strategos.core.content.TemplateSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** ART-01's colour and role rules: the faction reads from the tunic's dye, the role from what a body wears and holds. */
class UnitLookTest {
    static final TemplateSet T = TemplateSet.builtin();

    @Test
    void roleReadsFromTheGearAndOwnerFromTheDye() {
        UnitLook worker =
                UnitLook.of(Objects.requireNonNull(T.byId(TemplateSet.WORKER)).classes(), 0);
        UnitLook spearman =
                UnitLook.of(Objects.requireNonNull(T.byId(TemplateSet.INFANTRY)).classes(), 1);
        assertThat(worker.chest()).isEqualTo(UnitLook.TUNIC);
        assertThat(worker.head()).as("a worker's head is bare").isEmpty();
        assertThat(worker.rest()).as("its hands hold what its work gives it").isEmpty();
        assertThat(spearman.head()).isEqualTo("minecraft:iron_helmet");
        assertThat(spearman.rest()).isEqualTo("minecraft:trident");
        assertThat(spearman.shield()).isEqualTo("minecraft:shield");
        assertThat(worker.dye()).isEqualTo(HudTheme.DEFAULT.faction(0) & 0xFFFFFF);
        assertThat(spearman.dye()).as("the outline's colour").isEqualTo(HudTheme.DEFAULT.faction(1) & 0xFFFFFF);
        assertThat(spearman.shieldDye()).isEqualTo("red");
    }

    @Test
    void theDebugWalkerWearsNothing() {
        UnitLook walker = UnitLook.of(
                Objects.requireNonNull(T.byId(TemplateSet.DEBUG_WALKER)).classes(), 0);
        assertThat(java.util.List.of(
                        walker.head(), walker.chest(), walker.legs(), walker.feet(), walker.rest(), walker.shield()))
                .allMatch(String::isEmpty);
    }

    @Test
    void sevenFactionsSevenColours() {
        Set<Integer> dyes = new HashSet<>();
        Set<String> banners = new HashSet<>();
        for (int f = 0; f < 7; f++) {
            UnitLook look = UnitLook.of(java.util.List.of("Organic", "Unit"), f);
            dyes.add(look.dye());
            banners.add(look.shieldDye());
        }
        assertThat(dyes).hasSize(7);
        assertThat(banners).hasSize(7).doesNotContain("white");
    }
}
