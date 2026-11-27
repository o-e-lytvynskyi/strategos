package dev.strategos.core.client.hud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.client.input.Action;
import dev.strategos.core.client.input.ActionContext;
import dev.strategos.core.client.input.Binding;
import dev.strategos.core.client.input.DefaultBindings;
import dev.strategos.core.client.input.KeyCode;
import dev.strategos.core.client.order.OrderIntents;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.FormationMove;
import dev.strategos.core.protocol.FormationShape;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.Vec2d;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** ORD-06: the card offers the box and the line for two or more units, and a click arms the next ground click. */
class FormationCardTest {
    private static final TemplateSet SET = TemplateSet.builtin();
    private static final int INFANTRY =
            Objects.requireNonNull(SET.byId(TemplateSet.INFANTRY)).netIndex();
    private static final int WORKER =
            Objects.requireNonNull(SET.byId(TemplateSet.WORKER)).netIndex();

    private static List<CommandCard.Button> formations(List<Integer> units) {
        CommandCard card = CommandCard.of(
                new CommandCard.Input(
                        SET,
                        CommandCardTest.RESOURCES,
                        CommandCardResearchTest.faction(0, 0, List.of()),
                        units,
                        false,
                        List.of(),
                        false,
                        null,
                        null),
                null);
        return card.buttons().stream()
                .filter(b -> b.kind() == CommandCard.Kind.FORMATION)
                .toList();
    }

    @Test
    void twoOrMoreMovableUnitsOfferABoxAndALine() {
        List<CommandCard.Button> buttons = formations(List.of(INFANTRY, WORKER));
        assertThat(buttons).extracting(CommandCard.Button::template).containsExactly(0, 1);
        assertThat(buttons)
                .extracting(b -> FormationShape.ofCode(b.template()))
                .containsExactly(FormationShape.BOX, FormationShape.LINE);
        assertThat(buttons).extracting(CommandCard.Button::slot).doesNotHaveDuplicates();
        assertThat(buttons).allMatch(CommandCard.Button::affordable);
    }

    @Test
    void oneUnitOrNoneOffersNothing() {
        assertThat(formations(List.of(INFANTRY))).isEmpty();
        assertThat(formations(List.of())).isEmpty();
    }

    @Test
    void theButtonsKeepTheirOwnCellsBesideStopAndTheStances() {
        CommandCard card = CommandCard.of(
                new CommandCard.Input(
                        SET,
                        CommandCardTest.RESOURCES,
                        CommandCardResearchTest.faction(0, 0, List.of()),
                        List.of(INFANTRY, INFANTRY),
                        false,
                        List.of(),
                        false,
                        null,
                        null),
                null);
        assertThat(card.buttons()).extracting(CommandCard.Button::slot).doesNotHaveDuplicates();
    }

    @Test
    void aClickArmsTheShapeAndSendsNothingYet() {
        CardController controller = new CardController();
        List<Command> sent = new ArrayList<>();
        for (CommandCard.Button button : formations(List.of(INFANTRY, INFANTRY))) {
            CardController.Click click = controller.click(button, List.of(5, 6), null, SET, sent::add);
            assertThat(click.effect()).isEqualTo(CardController.Effect.FORMATION);
            assertThat(FormationShape.ofCode(click.template())).isEqualTo(FormationShape.ofCode(button.template()));
        }
        assertThat(sent).isEmpty();
        assertThat(controller
                        .click(formations(List.of(INFANTRY, INFANTRY)).get(0), List.of(5), null, SET, sent::add)
                        .effect())
                .as("one unit selected: nothing to arm")
                .isEqualTo(CardController.Effect.NONE);
    }

    @Test
    void theGroundClickMakesAReplacingFormationMoveOfTheSelection() {
        Command command = OrderIntents.formation(List.of(9, 3, 4), 12.5, -4, FormationShape.LINE);
        assertThat(command)
                .isEqualTo(new FormationMove(
                        UnitSet.of(9, 3, 4), new Vec2d(12.5, -4), FormationShape.LINE, QueueMode.REPLACE));
        assertThat(OrderIntents.formation(List.of(), 1, 1, FormationShape.BOX)).isNull();
    }

    @Test
    void theHotkeysAreBoundInRtsAndAreRebindableSoTheKeyReferenceShowsThem() {
        for (Action action : List.of(Action.FORMATION_BOX, Action.FORMATION_LINE)) {
            List<Binding> bound = DefaultBindings.all().stream()
                    .filter(b -> b.action() == action)
                    .toList();
            assertThat(bound).hasSize(1);
            assertThat(bound.get(0).context()).isEqualTo(ActionContext.RTS);
            assertThat(bound.get(0).rebindable()).isTrue();
        }
        List<Binding> all = DefaultBindings.all();
        assertThat(all.stream()
                        .filter(b -> b.key().equals(KeyCode.key('M')) && b.modifiers() == 0)
                        .map(Binding::action))
                .containsExactly(Action.FORMATION_BOX);
        assertThat(all.stream()
                        .filter(b -> b.key().equals(KeyCode.key('U')) && b.modifiers() == 0)
                        .map(Binding::action))
                .containsExactly(Action.FORMATION_LINE);
    }
}
