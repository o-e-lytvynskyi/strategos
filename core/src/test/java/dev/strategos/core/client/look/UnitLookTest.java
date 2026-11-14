package dev.strategos.core.client.look;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.client.hud.HudTheme;
import dev.strategos.core.content.TemplateSet;
import java.util.HashSet;
import java.util.List;
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
    void theRosterReadsFromWhatItHolds() {
        UnitLook archer = look("strategos:unit/archer");
        UnitLook horseman = look("strategos:unit/horseman");
        UnitLook spearman = look("strategos:unit/infantry");
        assertThat(archer.rest()).isEqualTo("minecraft:bow");
        assertThat(archer.shield()).as("a bow needs the other hand").isEmpty();
        assertThat(horseman.rest()).as("stone until the iron weapons").isEqualTo("minecraft:stone_sword");
        assertThat(horseman.shield()).isEqualTo("minecraft:shield");
        assertThat(spearman.rest()).isEqualTo("minecraft:trident");
    }

    /** TECH-03: the faction's tiers change the gear, a worker's none, a spearman keeps its spear. */
    @Test
    void theTiersChangeTheGear() {
        for (String id : List.of("strategos:unit/infantry", "strategos:unit/archer", "strategos:unit/horseman")) {
            List<String> classes = Objects.requireNonNull(T.byId(new dev.strategos.core.shared.TemplateId(id)))
                    .classes();
            UnitLook base = UnitLook.of(classes, 0, 0, 0);
            assertThat(base.chest()).as(id).isEqualTo(UnitLook.TUNIC);
            assertThat(UnitLook.of(classes, 0))
                    .as("the two-argument form is tier 0")
                    .isEqualTo(base);
            UnitLook armoured = UnitLook.of(classes, 0, 0, 1);
            assertThat(armoured.chest()).as(id).isEqualTo(UnitLook.CHAIN_TUNIC);
            assertThat(armoured.rest()).as("armour leaves the hands alone").isEqualTo(base.rest());
            UnitLook armed = UnitLook.of(classes, 0, 1, 0);
            assertThat(armed.chest()).as("a weapon leaves the body alone").isEqualTo(base.chest());
        }
        List<String> horse = Objects.requireNonNull(
                        T.byId(new dev.strategos.core.shared.TemplateId("strategos:unit/horseman")))
                .classes();
        assertThat(UnitLook.of(horse, 0, 0, 0).rest()).isEqualTo(UnitLook.STONE_SWORD);
        assertThat(UnitLook.of(horse, 0, 1, 0).rest()).isEqualTo(UnitLook.IRON_SWORD);
        List<String> spear =
                Objects.requireNonNull(T.byId(TemplateSet.INFANTRY)).classes();
        assertThat(UnitLook.of(spear, 0, 1, 1).rest())
                .as("a spearman keeps its spear")
                .isEqualTo("minecraft:trident");
        assertThat(UnitLook.of(spear, 0, 1, 1).legs()).isEqualTo(UnitLook.CHAIN_LEGGINGS);
        List<String> worker = Objects.requireNonNull(T.byId(TemplateSet.WORKER)).classes();
        assertThat(UnitLook.of(worker, 0, 1, 1)).as("no tier dresses a worker").isEqualTo(UnitLook.of(worker, 0));
    }

    private static UnitLook look(String id) {
        return UnitLook.of(
                Objects.requireNonNull(T.byId(new dev.strategos.core.shared.TemplateId(id)))
                        .classes(),
                0);
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
