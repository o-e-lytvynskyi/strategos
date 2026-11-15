package dev.strategos.mod.server.content;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.client.hud.CommandCard;
import dev.strategos.core.content.TechDef;
import dev.strategos.core.content.TemplateSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * QA-13.2 (spec §2.23): every entry a command card can show, of every built-in template and technology, has an icon
 * that names a real item of the game's registry: a building to place, a unit to train, a technology to research.
 */
class CommandCardIconsTest {
    private static final TemplateSet TEMPLATES = TemplateSet.builtin();

    @Test
    void everyBuildingAndUnitAndTechOnACardHasAnIconThatIsAnItem() {
        List<String> problems = new ArrayList<>();
        int entries = 0;
        for (int building : CommandCard.buildings(TEMPLATES)) {
            entries++;
            check(problems, "place " + TEMPLATES.byIndex(building).id(), CommandCard.icon(TEMPLATES, building));
        }
        for (var building : TEMPLATES.all()) {
            if (!building.isBuilding()) {
                continue;
            }
            for (int unit : CommandCard.trains(TEMPLATES, building.netIndex())) {
                entries++;
                check(problems, "train " + TEMPLATES.byIndex(unit).id(), CommandCard.icon(TEMPLATES, unit));
            }
            for (TechDef tech : CommandCard.researches(TEMPLATES, building.netIndex())) {
                entries++;
                check(problems, "research " + tech.id(), tech.icon());
            }
        }

        assertThat(entries).as("the cards have entries to check").isGreaterThan(10);
        assertThat(problems).isEmpty();
    }

    @Test
    void everyTemplateAndEveryTechnologyHasAnIconThatIsAnItem() {
        List<String> problems = new ArrayList<>();
        for (var t : TEMPLATES.all()) {
            check(problems, "template " + t.id(), CommandCard.icon(TEMPLATES, t.netIndex()));
        }
        for (TechDef tech : TEMPLATES.techs()) {
            check(problems, "tech " + tech.id(), tech.icon());
        }

        assertThat(TEMPLATES.techs()).as("the built-in technologies").isNotEmpty();
        assertThat(problems).isEmpty();
    }

    private static void check(List<String> problems, String what, String icon) {
        if (icon == null || icon.isBlank()) {
            problems.add(what + ": no icon");
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(icon);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            problems.add(what + ": icon '" + icon + "' is no item");
        }
    }
}
