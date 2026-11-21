package dev.strategos.core.client.hud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.CancelTrain;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.SetAutoReseed;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SharedBuildingCardTest {
    private static BuildingState.Entry foreign(int template) {
        return new BuildingState.Entry(
                123,
                template,
                1,
                4,
                4,
                0,
                1,
                CommandCard.COMPLETE,
                1000,
                255,
                false,
                List.of(),
                false,
                0,
                0,
                0,
                -1,
                0,
                false);
    }

    private static CommandCard.Input input(long stock) {
        return new CommandCard.Input(
                CommandCardTest.T, CommandCardTest.RESOURCES, CommandCardTest.faction(stock, stock), List.of(), false);
    }

    @Test
    void delegatedOrdersDoNotUseTheIssuersTreasuryOrInventTheOwnersQueue() {
        var building = foreign(CommandCardTest.CENTRE);
        assertThat(CommandCard.of(input(0), building).buttons()).isEmpty();
        assertThat(CommandCard.of(input(0), building, f -> f == 0).buttons()).isEmpty();
        var poor = CommandCard.of(input(0), building, f -> f == 0 || f == 1);
        var rich = CommandCard.of(input(1_000_000), building, f -> f == 0 || f == 1);
        assertThat(poor).isEqualTo(rich);
        assertThat(poor.buttons()).anySatisfy(button -> {
            assertThat(button.kind()).isEqualTo(CommandCard.Kind.TRAIN);
            assertThat(button.affordable()).isTrue();
            assertThat(button.cost()).isEmpty();
        });
        assertThat(building.queue()).isEmpty();
        var cancel = poor.buttons().stream()
                .filter(b -> b.kind() == CommandCard.Kind.CANCEL_NEXT)
                .findFirst()
                .orElseThrow();
        List<Command> sent = new ArrayList<>();
        new CardController().click(cancel, List.of(), building, CommandCardTest.T, sent::add);
        assertThat(sent).containsExactly(new CancelTrain(123, 0));
    }

    @Test
    void explicitReseedControlsDoNotToggleAnUnknownPrivateFlag() {
        int farmstead = CommandCardTest.index("strategos:building/farmstead");
        var unseen = foreign(farmstead);
        var card = CommandCard.of(input(0), unseen, f -> f == 1);
        var enable = card.buttons().stream()
                .filter(b -> b.kind() == CommandCard.Kind.AUTO_RESEED_ENABLE)
                .findFirst()
                .orElseThrow();
        var disable = card.buttons().stream()
                .filter(b -> b.kind() == CommandCard.Kind.AUTO_RESEED_DISABLE)
                .findFirst()
                .orElseThrow();
        List<Command> sent = new ArrayList<>();
        var controller = new CardController();
        controller.click(enable, List.of(), unseen, CommandCardTest.T, sent::add);
        controller.click(disable, List.of(), unseen, CommandCardTest.T, sent::add);
        assertThat(sent).containsExactly(new SetAutoReseed(123, true), new SetAutoReseed(123, false));
    }

    @Test
    void currentPublicTrainingListsFitWithoutHiddenButtonsOrOverlappingCells() {
        for (var template : CommandCardTest.T.all()) {
            if (!template.isBuilding()) continue;
            var building = foreign(template.netIndex());
            var card = CommandCard.of(input(0), building, f -> f == 1);
            assertThat(card.buttons())
                    .extracting(CommandCard.Button::slot)
                    .doesNotHaveDuplicates()
                    .allSatisfy(slot -> assertThat(slot).isBetween(0, CommandCard.SLOTS - 1));
            assertThat(card.buttons().stream()
                            .filter(b -> b.kind() == CommandCard.Kind.TRAIN)
                            .map(CommandCard.Button::template)
                            .toList())
                    .containsExactlyElementsOf(CommandCard.trains(CommandCardTest.T, template.netIndex()));
        }
    }
}
