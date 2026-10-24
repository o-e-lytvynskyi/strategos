package dev.strategos.core.client.hud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.BuildingState;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * UI-09.1 in L1: a worker's build page lists every building a worker places, by net index, each with its cost and its
 * requirement (the limit), in the grid's cells with their keys, back in the last; and each own foundation or site has
 * its progress bar over it, filled to its progress.
 */
class BuildPageTest {
    static final TemplateSet T = CommandCardTest.T;

    @Test
    void theBuildPageListsEveryBuildingWithItsCostAndRequirement() {
        var rich = new dev.strategos.core.protocol.FactionState(
                CommandCardTest.MATCH,
                0,
                10,
                List.of(10_000_000L, 10_000_000L, 10_000_000L, 10_000_000L),
                List.of(0L, 0L, 0L, 0L),
                List.of(0L, 0L, 0L, 0L),
                3,
                20,
                1,
                List.of(0, 0, 0, 0));
        var model = CommandCardTest.model(rich, CommandCardTest.centre(List.of(), 0));
        CommandCard page = CommandCard.of(
                new CommandCard.Input(
                        T,
                        CommandCardTest.RESOURCES,
                        model.faction(),
                        List.of(CommandCardTest.WORKER),
                        true,
                        model.buildings(),
                        false),
                null);
        List<CommandCard.Button> builds = page.buttons().stream()
                .filter(b -> b.kind() == CommandCard.Kind.BUILD)
                .toList();
        assertThat(builds).extracting(CommandCard.Button::template).containsExactlyElementsOf(CommandCard.buildings(T));
        assertThat(builds)
                .extracting(b -> T.byIndex(b.template()).id().value())
                .contains("strategos:building/house", "strategos:building/storehouse", "strategos:building/farmstead")
                .doesNotContain("strategos:building/quarry", "strategos:building/iron_mine");
        for (int k = 0; k < builds.size(); k++) {
            assertThat(builds.get(k).slot()).as("in reading order").isEqualTo(k);
            assertThat(builds.get(k).cost())
                    .as("its cost")
                    .isEqualTo(CommandCard.cost(
                            new CommandCard.Input(T, CommandCardTest.RESOURCES, model.faction(), List.of(), true),
                            builds.get(k).template(),
                            "Cost/resources/"));
        }
        assertThat(builds.stream().filter(b -> b.reason() != null))
                .as("the second centre: its limit, the only requirement")
                .singleElement()
                .satisfies(b -> assertThat(b.reason()).isEqualTo("Limit reached"));
        assertThat(page.buttons())
                .filteredOn(b -> b.kind() == CommandCard.Kind.BACK)
                .singleElement()
                .satisfies(b -> assertThat(CommandCard.hotkey(b.slot())).isEqualTo('B'));
    }

    @Test
    void eachOwnSiteHasItsProgressBar() {
        int house = CommandCardTest.index("strategos:building/house");
        BuildingState.Entry site = new BuildingState.Entry(
                (1 << 20) + 2, house, 0, 10, 20, 0, 5, 1, 400, 100, true, List.of(), false, 0, 0, 0, -1, 0);
        BuildingState.Entry theirs = new BuildingState.Entry(
                (1 << 20) + 3, house, 1, 30, 20, 0, 5, 1, 700, 100, false, List.of(), false, 0, 0, 0, -1, 0);
        BuildingState.Entry done = CommandCardTest.centre(List.of(), 0);
        List<HealthBars.Bar> bars = HealthBars.sites(List.of(site, theirs, done), T, 1000, -2000);
        assertThat(bars).as("the own site only").hasSize(1);
        HealthBars.Bar bar = bars.get(0);
        assertThat(bar.fill()).isEqualTo(0.4);
        assertThat(bar.x()).isEqualTo(1000 + 10 + 2.5);
        assertThat(bar.z()).isEqualTo(-2000 + 20 + 2.5);
        assertThat(bar.y()).as("above the house's five layers").isEqualTo(5 + 5 + 0.5);
        assertThat(bar.argb()).isEqualTo(HealthBars.SITE);
    }
}
