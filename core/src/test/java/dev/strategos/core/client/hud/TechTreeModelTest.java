package dev.strategos.core.client.hud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TechDef;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.FactionState;
import dev.strategos.core.shared.QueueEntry;
import dev.strategos.core.shared.TechId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** TECH-05: the technology tree's view-model: phases as columns, lines as rows, and each technology's state. */
class TechTreeModelTest {
    private static final TemplateSet T = CommandCardTest.T;

    private static int index(String tech) {
        return Objects.requireNonNull(T.tech(new TechId("strategos:" + tech)), tech)
                .netIndex();
    }

    private static TechTreeModel tree(List<String> researched, List<String> queued) {
        List<Integer> done = new ArrayList<>();
        for (String id : researched) {
            done.add(index(id));
        }
        List<Integer> queue = new ArrayList<>();
        for (String id : queued) {
            queue.add(QueueEntry.techEntry(index(id)));
        }
        FactionState faction = CommandCardResearchTest.faction(0, 0, done);
        List<BuildingState.Entry> buildings =
                List.of(CommandCardResearchTest.building(7, CommandCardResearchTest.STOREHOUSE, queue, 0));
        return TechTreeModel.of(T, new ClientFactionView(T, faction, buildings, null), null, buildings);
    }

    private static TechTreeModel.Cell cell(TechTreeModel tree, String tech) {
        return tree.rows().stream()
                .flatMap(r -> r.cells().stream())
                .flatMap(List::stream)
                .filter(c -> c.tech().id().value().equals("strategos:" + tech))
                .findFirst()
                .orElseThrow();
    }

    private static TechTreeModel.State state(TechTreeModel tree, String tech) {
        return cell(tree, tech).state();
    }

    @Test
    void phasesAreTheColumnsInOrdinalOrderUnderTheirNames() {
        TechTreeModel tree = tree(List.of(), List.of());
        assertThat(tree.columns())
                .extracting(TechTreeModel.Column::ordinal, TechTreeModel.Column::nameKey)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(0, "strategos.phase.0"),
                        org.assertj.core.groups.Tuple.tuple(1, "strategos.phase.1"),
                        org.assertj.core.groups.Tuple.tuple(2, "strategos.phase.2"));
    }

    @Test
    void aTechnologyStandsInTheColumnOfThePhaseItNeedsAndEveryOneIsInExactlyOneRow() {
        TechTreeModel tree = tree(List.of(), List.of());
        for (int c = 0; c < 3; c++) {
            final int column = c;
            assertThat(tree.rows().stream()
                            .flatMap(r -> r.cells().get(column).stream())
                            .map(x -> x.tech().id().value().substring(10)))
                    .as("column %d", c)
                    .contains(List.of("gather_wood_1", "gather_wood_2", "gather_wood_3")
                            .get(c));
        }
        long techs = T.techs().stream().filter(t -> !t.isPhase()).count();
        assertThat(tree.rows().stream()
                        .mapToLong(r -> r.cells().stream().mapToLong(List::size).sum())
                        .sum())
                .isEqualTo(techs);
        assertThat(tree.rows().stream().map(TechTreeModel.Row::line)).contains("gather_wood", "carry", "guild_lumber");
    }

    @Test
    void aPairSharesARowAndATierChainSharesOne() {
        TechTreeModel tree = tree(List.of(), List.of());
        TechTreeModel.Row guilds = tree.rows().stream()
                .filter(r -> r.line().equals("guild_lumber"))
                .findFirst()
                .orElseThrow();
        assertThat(guilds.cells().get(1))
                .extracting(c -> c.tech().id().value())
                .containsExactlyInAnyOrder("strategos:guild_lumber", "strategos:guild_mining");
        TechTreeModel.Row wood = tree.rows().stream()
                .filter(r -> r.line().equals("gather_wood"))
                .findFirst()
                .orElseThrow();
        assertThat(wood.cells().stream().mapToInt(List::size)).containsExactly(1, 1, 1);
    }

    @Test
    void theStatesFollowTheResearchAndPhaseTheFactionHas() {
        TechTreeModel start = tree(List.of(), List.of());
        assertThat(state(start, "gather_wood_1")).isEqualTo(TechTreeModel.State.AVAILABLE);
        assertThat(state(start, "gather_wood_2")).as("phase first").isEqualTo(TechTreeModel.State.NEEDS_PHASE);
        assertThat(start.columns().get(1).state()).isEqualTo(TechTreeModel.State.NEEDS_BUILDINGS);

        TechTreeModel town = tree(List.of("phase_town"), List.of());
        assertThat(town.columns().get(1).state()).isEqualTo(TechTreeModel.State.RESEARCHED);
        assertThat(state(town, "gather_wood_2")).isEqualTo(TechTreeModel.State.NEEDS_PREVIOUS);
        assertThat(state(town, "iron_weapons")).isEqualTo(TechTreeModel.State.AVAILABLE);
        assertThat(state(town, "diamond_weapons")).isEqualTo(TechTreeModel.State.NEEDS_PREVIOUS);
        assertThat(state(town, "gather_wood_3")).isEqualTo(TechTreeModel.State.NEEDS_PHASE);

        TechTreeModel tier = tree(List.of("phase_town", "gather_wood_1"), List.of("gather_stone_1"));
        assertThat(state(tier, "gather_wood_1")).isEqualTo(TechTreeModel.State.RESEARCHED);
        assertThat(state(tier, "gather_wood_2")).isEqualTo(TechTreeModel.State.AVAILABLE);
        assertThat(state(tier, "gather_stone_1")).isEqualTo(TechTreeModel.State.RESEARCHING);
    }

    @Test
    void thePairOfAResearchedOrQueuedTechnologyIsLockedAndNamesIt() {
        TechTreeModel researched = tree(List.of("phase_town", "guild_mining"), List.of());
        assertThat(state(researched, "guild_lumber")).isEqualTo(TechTreeModel.State.LOCKED);
        assertThat(cell(researched, "guild_lumber").lockedBy()).isEqualTo("guild_mining");
        assertThat(state(researched, "guild_mining")).isEqualTo(TechTreeModel.State.RESEARCHED);

        TechTreeModel queued = tree(List.of("phase_town"), List.of("phalanx"));
        assertThat(state(queued, "skirmish_drill")).isEqualTo(TechTreeModel.State.LOCKED);
        assertThat(cell(queued, "skirmish_drill").lockedBy()).isEqualTo("phalanx");
        assertThat(state(queued, "phalanx")).isEqualTo(TechTreeModel.State.RESEARCHING);
    }

    @Test
    void whatTheCivilisationsRosterLacksIsOutsideAndTheCellNamesItsResearcherCostAndEffects() {
        FactionState faction = CommandCardResearchTest.faction(0, 0, List.of());
        TechTreeModel tree = TechTreeModel.of(
                T,
                new ClientFactionView(T, faction, List.of(), CommandCardRosterTest.LEAN),
                CommandCardRosterTest.LEAN,
                List.of());
        assertThat(state(tree, "gather_wood_1")).isEqualTo(TechTreeModel.State.OUTSIDE_ROSTER);
        assertThat(state(tree, "gather_stone_1")).isEqualTo(TechTreeModel.State.AVAILABLE);

        TechTreeModel.Cell wood2 = cell(tree(List.of(), List.of()), "gather_wood_2");
        TechDef def = wood2.tech();
        assertThat(wood2.researcher())
                .as("a Town tier, listed by the phase's tokens")
                .isEqualTo("strategos.building.storehouse");
        assertThat(wood2.cost()).isEqualTo(def.cost());
        assertThat(wood2.time()).isEqualTo(45.0);
        assertThat(wood2.effects()).containsExactly("ResourceGatherer/rates/wood/tree mul 1.4 for Worker");
    }
}
