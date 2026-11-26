package dev.strategos.core.client.hud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.client.input.Action;
import dev.strategos.core.client.input.ActionContext;
import dev.strategos.core.client.input.ActionMap;
import dev.strategos.core.client.input.Binding;
import dev.strategos.core.client.input.DefaultBindings;
import dev.strategos.core.client.input.KeyCode;
import dev.strategos.core.client.input.Modifiers;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.BuildingState;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * UI-17.1 in L1: every command-card action has a hotkey. The cards a player meets (the units', the build page, each
 * building's) and every kind of button a card has are enumerated; each button's cell is pressed by a bound card key,
 * and the actions that have a key of their own have it bound too.
 */
class HotkeyCoverageTest {
    static final TemplateSet T = TemplateSet.builtin();
    static final int WORKER = Objects.requireNonNull(T.byId(TemplateSet.WORKER)).netIndex();
    static final int INFANTRY =
            Objects.requireNonNull(T.byId(TemplateSet.INFANTRY)).netIndex();

    /** The card button kinds with a key of their own beside the cell's; the rest are reached by the card keys alone. */
    private static final Map<CommandCard.Kind, List<Action>> OWN_KEYS = new EnumMap<>(CommandCard.Kind.class);

    static {
        OWN_KEYS.put(CommandCard.Kind.STOP, List.of(Action.STOP));
        OWN_KEYS.put(CommandCard.Kind.BUILD_PAGE, List.of(Action.BUILD));
        OWN_KEYS.put(CommandCard.Kind.BUILD, List.of(Action.BUILD));
        OWN_KEYS.put(CommandCard.Kind.DEMOLISH, List.of(Action.DELETE_SELECTED));
        OWN_KEYS.put(CommandCard.Kind.CANCEL_BUILDING, List.of(Action.DELETE_SELECTED));
        OWN_KEYS.put(CommandCard.Kind.ALL_CLEAR, List.of(Action.BACK_TO_WORK));
        OWN_KEYS.put(CommandCard.Kind.FORMATION, List.of(Action.FORMATION_BOX, Action.FORMATION_LINE));
        OWN_KEYS.put(CommandCard.Kind.PATROL, List.of(Action.PATROL));
        OWN_KEYS.put(CommandCard.Kind.GUARD, List.of(Action.GUARD));
    }

    /** Every button on the cards a player can meet. */
    private static List<CommandCard.Button> buttons() {
        List<CommandCard.Button> out = new ArrayList<>();
        ClientMatchModelHolder holder = new ClientMatchModelHolder();
        for (List<Integer> units : List.of(List.of(WORKER), List.of(INFANTRY, INFANTRY), List.of(WORKER, INFANTRY))) {
            for (boolean page : List.of(false, true)) {
                out.addAll(CommandCardTest.card(holder.model, units, null, page).buttons());
            }
        }
        for (String id : List.of("civic_centre", "house", "field", "barracks", "tower")) {
            int template = CommandCardTest.index("strategos:building/" + id);
            BuildingState.Entry own = new BuildingState.Entry(
                    0x100001,
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
                    List.of(),
                    false,
                    0,
                    0,
                    0,
                    -1,
                    0);
            out.addAll(CommandCardTest.card(holder.model, List.of(), own, false).buttons());
            BuildingState.Entry site = new BuildingState.Entry(
                    0x100002, template, 0, 4, 4, 0, 1, 1, 10, 255, true, List.of(), false, 0, 0, 0, -1, 0);
            out.addAll(
                    CommandCardTest.card(holder.model, List.of(), site, false).buttons());
        }
        return out;
    }

    private static final class ClientMatchModelHolder {
        final dev.strategos.core.client.model.ClientMatchModel model =
                CommandCardTest.model(CommandCardTest.faction(1000, 1000));
    }

    private static Binding binding(Action action) {
        return DefaultBindings.all().stream()
                .filter(b -> b.action() == action && b.context() == ActionContext.RTS)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no binding for " + action));
    }

    @Test
    void everyCellOfTheCardHasAnAltKey() {
        for (int slot = 0; slot < CommandCard.SLOTS; slot++) {
            Action cell = Action.valueOf("CARD_" + (slot + 1));
            assertThat(cell.cardSlot()).isEqualTo(slot);
            Binding b = binding(cell);
            assertThat(b.rebindable()).isTrue();
            assertThat(b.modifiers()).isEqualTo(Modifiers.ALT);
            assertThat(b.key()).isEqualTo(KeyCode.key(CommandCard.hotkey(slot)));
            assertThat(ActionMap.defaults().resolve(ActionContext.RTS, b.key(), Modifiers.ALT))
                    .as("Alt + " + CommandCard.hotkey(slot))
                    .isEqualTo(cell);
        }
    }

    @Test
    void everyButtonOfEveryCardSitsInACellThatHasAKey() {
        List<CommandCard.Button> all = buttons();
        assertThat(all)
                .extracting(CommandCard.Button::kind)
                .contains(
                        CommandCard.Kind.TRAIN,
                        CommandCard.Kind.BUILD,
                        CommandCard.Kind.BACK,
                        CommandCard.Kind.STOP,
                        CommandCard.Kind.STANCE,
                        CommandCard.Kind.DEMOLISH,
                        CommandCard.Kind.RING_BELL,
                        CommandCard.Kind.ALL_CLEAR,
                        CommandCard.Kind.CANCEL_BUILDING,
                        CommandCard.Kind.PATROL,
                        CommandCard.Kind.GUARD,
                        CommandCard.Kind.FORMATION);
        Set<Integer> cells = new java.util.TreeSet<>();
        for (CommandCard.Button b : all) {
            cells.add(b.slot());
        }
        for (int slot : cells) {
            assertThat(slot).isBetween(0, CommandCard.SLOTS - 1);
            assertThat(Action.values())
                    .as("a card action for cell " + slot)
                    .anyMatch(a -> a.cardSlot() == slot && binding(a).modifiers() == Modifiers.ALT);
        }
    }

    @Test
    void everyKindOfButtonIsAccountedFor() {
        Set<CommandCard.Kind> reached = EnumSet.noneOf(CommandCard.Kind.class);
        for (CommandCard.Button b : buttons()) {
            reached.add(b.kind());
        }
        for (CommandCard.Kind kind : CommandCard.Kind.values()) {
            for (int slot = 0; slot < CommandCard.SLOTS; slot++) {
                CommandCard.Button b = new CommandCard.Button(kind, slot, -1, List.of(), true);
                assertThat(binding(Action.valueOf("CARD_" + (b.slot() + 1))).key())
                        .as(kind + " in cell " + slot)
                        .isEqualTo(KeyCode.key(CommandCard.hotkey(b.slot())));
            }
            for (Action own : OWN_KEYS.getOrDefault(kind, List.of())) {
                assertThat(binding(own).rebindable())
                        .as(kind + "'s own key " + own)
                        .isTrue();
            }
        }
        assertThat(reached)
                .as("the enumerated cards reach every button kind a plain selection or building offers")
                .contains(
                        CommandCard.Kind.TRAIN,
                        CommandCard.Kind.BUILD_PAGE,
                        CommandCard.Kind.STOP,
                        CommandCard.Kind.DEMOLISH);
    }

    @Test
    void theNewActionsAreBoundAndNothingSharesAKeyInItsContext() {
        for (Action a : List.of(
                Action.BACK_TO_WORK,
                Action.GARRISON,
                Action.DELETE_SELECTED,
                Action.NEXT_IDLE_SOLDIER,
                Action.NEXT_IDLE_WORKER,
                Action.SELECT_ALL_SOLDIERS,
                Action.SELECT_ALL_WORKERS,
                Action.STOP,
                Action.SET_BOOKMARK_1,
                Action.SET_BOOKMARK_4,
                Action.JUMP_BOOKMARK_1,
                Action.JUMP_BOOKMARK_4)) {
            assertThat(binding(a).rebindable()).as(a.name()).isTrue();
        }
        List<Binding> all = DefaultBindings.all();
        for (int i = 0; i < all.size(); i++) {
            for (int j = 0; j < i; j++) {
                Binding a = all.get(i);
                Binding b = all.get(j);
                assertThat(a.context() == b.context() && a.key().equals(b.key()) && a.modifiers() == b.modifiers())
                        .as(a.action() + " and " + b.action() + " on one key")
                        .isFalse();
            }
        }
    }

    @Test
    void theModifiedKeysWinOverThePlainKeysTheyShare() {
        ActionMap map = ActionMap.defaults();
        ActionContext rts = ActionContext.RTS;
        assertThat(map.resolve(rts, KeyCode.key(KeyCode.I), Modifiers.NONE)).isEqualTo(Action.NEXT_IDLE_WORKER);
        assertThat(map.resolve(rts, KeyCode.key(KeyCode.I), Modifiers.SHIFT)).isEqualTo(Action.NEXT_IDLE_SOLDIER);
        assertThat(map.resolve(rts, KeyCode.key(KeyCode.A), Modifiers.NONE)).isEqualTo(Action.CAMERA_PAN_LEFT);
        assertThat(map.resolve(rts, KeyCode.key(KeyCode.A), Modifiers.CTRL)).isEqualTo(Action.SELECT_ALL_SOLDIERS);
        assertThat(map.resolve(rts, KeyCode.key(KeyCode.W), Modifiers.CTRL)).isEqualTo(Action.SELECT_ALL_WORKERS);
        assertThat(map.resolve(rts, KeyCode.key(KeyCode.F4), Modifiers.NONE)).isEqualTo(Action.JUMP_BOOKMARK_1);
        assertThat(map.resolve(rts, KeyCode.key(KeyCode.F4), Modifiers.CTRL)).isEqualTo(Action.SET_BOOKMARK_1);
        assertThat(map.resolve(rts, KeyCode.key(KeyCode.F8), Modifiers.CTRL)).isEqualTo(Action.SET_BOOKMARK_4);
        assertThat(map.resolve(rts, KeyCode.key(KeyCode.F8), Modifiers.NONE)).isEqualTo(Action.JUMP_BOOKMARK_4);
        assertThat(map.resolve(rts, KeyCode.key(KeyCode.T), Modifiers.NONE)).isEqualTo(Action.BACK_TO_WORK);
        assertThat(map.resolve(rts, KeyCode.key(KeyCode.SEMICOLON), Modifiers.NONE))
                .isEqualTo(Action.GARRISON);
        assertThat(map.resolve(rts, KeyCode.key(KeyCode.BACKSPACE), Modifiers.NONE))
                .isEqualTo(Action.DELETE_SELECTED);
        assertThat(map.resolve(rts, KeyCode.digit(1), Modifiers.CTRL))
                .as("the control groups stay on the digits")
                .isEqualTo(Action.CONTROL_GROUP_SET);
    }
}
