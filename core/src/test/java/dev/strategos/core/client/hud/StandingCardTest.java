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
import dev.strategos.core.protocol.Guard;
import dev.strategos.core.protocol.Patrol;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.Vec2d;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** ORD-07: the card offers patrol and guard for a selection that walks, and a click arms the targeting. */
class StandingCardTest {
    private static final TemplateSet SET = TemplateSet.builtin();
    private static final int INFANTRY =
            Objects.requireNonNull(SET.byId(TemplateSet.INFANTRY)).netIndex();
    private static final int WORKER =
            Objects.requireNonNull(SET.byId(TemplateSet.WORKER)).netIndex();

    private static CommandCard card(List<Integer> units) {
        return CommandCard.of(
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
    }

    private static List<CommandCard.Button> standing(List<Integer> units) {
        return card(units).buttons().stream()
                .filter(b -> b.kind() == CommandCard.Kind.PATROL || b.kind() == CommandCard.Kind.GUARD)
                .toList();
    }

    @Test
    void oneWalkingUnitOffersAPatrolAndAGuardInTheirOwnCells() {
        for (List<Integer> units : List.of(List.of(INFANTRY), List.of(WORKER), List.of(INFANTRY, INFANTRY))) {
            List<CommandCard.Button> buttons = standing(units);
            assertThat(buttons)
                    .extracting(CommandCard.Button::kind)
                    .containsExactly(CommandCard.Kind.PATROL, CommandCard.Kind.GUARD);
            assertThat(buttons)
                    .extracting(CommandCard.Button::slot)
                    .containsExactly(StandingCard.PATROL_SLOT, StandingCard.GUARD_SLOT);
            assertThat(buttons).allMatch(CommandCard.Button::affordable);
            assertThat(card(units).buttons())
                    .extracting(CommandCard.Button::slot)
                    .doesNotHaveDuplicates();
        }
    }

    @Test
    void noSelectionOffersNothing() {
        assertThat(standing(List.of())).isEmpty();
    }

    @Test
    void aClickArmsTheTargetingAndSendsNothingYet() {
        CardController controller = new CardController();
        List<Command> sent = new ArrayList<>();
        List<CommandCard.Button> buttons = standing(List.of(INFANTRY));
        CardController.Click patrol = controller.click(buttons.get(0), List.of(5), null, SET, sent::add);
        CardController.Click guard = controller.click(buttons.get(1), List.of(5), null, SET, sent::add);

        assertThat(patrol.effect()).isEqualTo(CardController.Effect.PATROL);
        assertThat(guard.effect()).isEqualTo(CardController.Effect.GUARD);
        assertThat(sent).isEmpty();
        assertThat(controller
                        .click(buttons.get(0), List.of(), null, SET, sent::add)
                        .effect())
                .as("nothing selected: nothing to arm")
                .isEqualTo(CardController.Effect.NONE);
    }

    @Test
    void theClicksMakeReplacingOrdersOfTheSelection() {
        List<Vec2d> points = List.of(new Vec2d(12.5, -4), new Vec2d(30, 9));
        assertThat(OrderIntents.patrol(List.of(9, 3), points))
                .isEqualTo(new Patrol(UnitSet.of(9, 3), points, QueueMode.REPLACE));
        assertThat(OrderIntents.patrol(List.of(), points)).isNull();
        assertThat(OrderIntents.patrol(List.of(9), List.of())).isNull();
        assertThat(OrderIntents.guard(List.of(9, 3), 0x80007))
                .isEqualTo(new Guard(UnitSet.of(9, 3), 0x80007, QueueMode.REPLACE));
        assertThat(OrderIntents.guard(List.of(), 0x80007)).isNull();
        assertThat(OrderIntents.guard(List.of(9), 0)).isNull();
    }

    @Test
    void theHotkeysAreBoundInRtsAndPatrolAndGuardAreRebindableSoTheKeyReferenceShowsThem() {
        for (Action action : List.of(Action.PATROL, Action.GUARD)) {
            List<Binding> bound = DefaultBindings.all().stream()
                    .filter(b -> b.action() == action)
                    .toList();
            assertThat(bound).hasSize(1);
            assertThat(bound.get(0).context()).isEqualTo(ActionContext.RTS);
            assertThat(bound.get(0).rebindable()).isTrue();
        }
        List<Binding> all = DefaultBindings.all();
        assertThat(all.stream()
                        .filter(b -> b.key().equals(KeyCode.key('O')) && b.modifiers() == 0)
                        .map(Binding::action))
                .containsExactly(Action.PATROL);
        assertThat(all.stream()
                        .filter(b -> b.key().equals(KeyCode.key(KeyCode.G)) && b.modifiers() == 0)
                        .filter(b -> b.context() == ActionContext.RTS)
                        .map(Binding::action))
                .containsExactly(Action.GUARD);
        assertThat(all.stream().filter(b -> b.action() == Action.CONFIRM))
                .singleElement()
                .satisfies(b -> {
                    assertThat(b.key()).isEqualTo(KeyCode.key(KeyCode.ENTER));
                    assertThat(b.rebindable()).as("Enter is fixed, like Escape").isFalse();
                });
    }
}
