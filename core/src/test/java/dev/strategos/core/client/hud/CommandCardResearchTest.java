package dev.strategos.core.client.hud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.client.model.ClientMatchModel;
import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.FactionState;
import dev.strategos.core.protocol.Research;
import dev.strategos.core.shared.QueueEntry;
import dev.strategos.core.shared.TechId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** UI-13: a building's researchable techs as card buttons, what they cost and say, and why one is disabled. */
class CommandCardResearchTest {
    static final int STOREHOUSE = CommandCardTest.index("strategos:building/storehouse");
    static final int HOUSE = CommandCardTest.index("strategos:building/house");
    static final int WOOD_1 = Objects.requireNonNull(CommandCardTest.T.tech(new TechId("strategos:gather_wood_1")))
            .netIndex();

    static BuildingState.Entry building(int handle, int template, List<Integer> queue, int trained) {
        return new BuildingState.Entry(
                handle,
                template,
                0,
                4,
                4,
                0,
                1,
                CommandCard.COMPLETE,
                1000,
                255,
                true,
                queue,
                false,
                0,
                0,
                trained,
                -1,
                0);
    }

    static FactionState faction(long food, long wood, List<Integer> researched) {
        return new FactionState(
                CommandCardTest.MATCH,
                0,
                10,
                List.of(food, wood, 0L, 0L),
                List.of(0L, 0L, 0L, 0L),
                List.of(0L, 0L, 0L, 0L),
                3,
                20,
                1,
                List.of(0, 0, 0, 0),
                researched,
                0);
    }

    static List<CommandCard.Button> research(ClientMatchModel model, BuildingState.Entry selected) {
        CommandCard card = CommandCard.of(
                new CommandCard.Input(
                        CommandCardTest.T,
                        CommandCardTest.RESOURCES,
                        model.faction(),
                        List.of(),
                        false,
                        model.buildings(),
                        false,
                        null),
                selected);
        return card.buttons().stream()
                .filter(b -> b.kind() == CommandCard.Kind.RESEARCH)
                .toList();
    }

    @Test
    void aTechIsAButtonWithCostTimeAndEffects() {
        BuildingState.Entry shed = building(7, STOREHOUSE, List.of(), 0);
        ClientMatchModel model = CommandCardTest.model(faction(500_000, 500_000, List.of()), shed);
        List<CommandCard.Button> buttons = research(model, model.buildings().get(0));
        assertThat(buttons)
                .containsExactly(new CommandCard.Button(
                        CommandCard.Kind.RESEARCH, 0, WOOD_1, List.of(100_000L, 50_000L, 0L, 0L), true));
        var tech = Objects.requireNonNull(CommandCardTest.T.techByIndex(WOOD_1));
        assertThat(tech.time()).isEqualTo(30.0);
        assertThat(CommandCard.techEffects(tech))
                .containsExactly("ResourceGatherer/rates/wood/tree mul 1.2 for Worker");
    }

    @Test
    void whatCannotBeAffordedIsDisabledWithItsReason() {
        BuildingState.Entry shed = building(7, STOREHOUSE, List.of(), 0);
        ClientMatchModel model = CommandCardTest.model(faction(99_000, 500_000, List.of()), shed);
        CommandCard.Button b = research(model, model.buildings().get(0)).get(0);
        assertThat(b.affordable()).isFalse();
        assertThat(b.reason()).isNotBlank();
    }

    @Test
    void unmetRequirementsAreNamedAndMetOnesEnableTheButton() {
        BuildingState.Entry centre = CommandCardTest.centre(List.of(), 0);
        BuildingState.Entry[] entries = new BuildingState.Entry[6];
        entries[0] = centre;
        for (int i = 1; i < 6; i++) {
            entries[i] = building(100 + i, HOUSE, List.of(), 0);
        }
        BuildingState.Entry[] three = java.util.Arrays.copyOf(entries, 4);
        ClientMatchModel few = CommandCardTest.model(CommandCardTest.faction(900_000, 900_000), three);
        CommandCard.Button short3 = research(few, few.buildings().get(0)).get(0);
        assertThat(short3.affordable()).isFalse();
        assertThat(short3.reason()).isEqualTo("Needs 5 Village (3)");

        ClientMatchModel five = CommandCardTest.model(faction(900_000, 900_000, List.of()), entries);
        CommandCard.Button ready = research(five, five.buildings().get(0)).get(0);
        assertThat(ready.reason())
                .as("requirements met; the treasury is the only gate left")
                .isNotEqualTo("Needs");
        assertThat(ready.kind()).isEqualTo(CommandCard.Kind.RESEARCH);
    }

    @Test
    void aQueuedTechIsDisabledAndAResearchedOneIsGone() {
        int entry = QueueEntry.techEntry(WOOD_1);
        BuildingState.Entry shed = building(7, STOREHOUSE, List.of(entry), 450);
        ClientMatchModel model = CommandCardTest.model(faction(500_000, 500_000, List.of()), shed);
        BuildingState.Entry arrived = model.buildings().get(0);
        assertThat(arrived.queue()).as("the research entry crossed the wire").containsExactly(entry);
        assertThat(arrived.trained()).as("and its progress").isEqualTo(450);
        CommandCard.Button queued = research(model, arrived).get(0);
        assertThat(queued.affordable()).isFalse();
        assertThat(queued.reason()).isEqualTo("Queued");

        ClientMatchModel done = CommandCardTest.model(
                faction(500_000, 500_000, List.of(WOOD_1)), building(7, STOREHOUSE, List.of(), 0));
        assertThat(research(done, done.buildings().get(0))).isEmpty();
    }

    @Test
    void aClickSendsOneResearch() {
        BuildingState.Entry shed = building(7, STOREHOUSE, List.of(), 0);
        ClientMatchModel model = CommandCardTest.model(faction(500_000, 500_000, List.of()), shed);
        BuildingState.Entry b = model.buildings().get(0);
        CommandCard card = CommandCardTest.card(model, List.of(), b, false);
        List<Command> sent = new ArrayList<>();
        double[] at = CommandCardTest.centreOf(CommandCard.cell(CommandCardTest.CARD, CommandCardTest.PAD, 0));
        CardController.Click click = new CardController()
                .click(
                        card.at(CommandCardTest.CARD, CommandCardTest.PAD, at[0], at[1]),
                        List.of(7),
                        b,
                        CommandCardTest.T,
                        sent::add);
        assertThat(click.effect()).isEqualTo(CardController.Effect.SENT);
        assertThat(sent).containsExactly(new Research(7, new TechId("strategos:gather_wood_1")));
    }
}
