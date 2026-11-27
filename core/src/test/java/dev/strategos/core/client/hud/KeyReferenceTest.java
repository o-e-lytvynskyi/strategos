package dev.strategos.core.client.hud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.client.hud.ControlsOverlay.Row;
import dev.strategos.core.client.input.Action;
import dev.strategos.core.client.input.ActionContext;
import dev.strategos.core.client.input.ActionMap;
import dev.strategos.core.client.input.Binding;
import dev.strategos.core.client.input.KeyCode;
import dev.strategos.core.client.input.Modifiers;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * UI-17.2 in L1: the in-game key reference is generated from the bindings: every rebindable action has a row under its
 * name, on the key it is bound to now, and a rebind changes the row and nothing else.
 */
class KeyReferenceTest {
    /** The label a binding's key shows as: its modifiers and key code, as the client's key mapping would give them. */
    private static String label(Binding b) {
        String mods = (Modifiers.ctrl(b.modifiers()) ? "Ctrl+" : "")
                + (Modifiers.alt(b.modifiers()) ? "Alt+" : "")
                + (Modifiers.shift(b.modifiers()) ? "Shift+" : "");
        return mods + b.key().kind() + b.key().code();
    }

    private static List<Row> expected(ActionMap map) {
        List<Row> rows = new ArrayList<>();
        for (Binding b : map.bindings()) {
            if (b.rebindable()) {
                rows.add(new Row(b.mappingName(), label(b)));
            }
        }
        return rows;
    }

    @Test
    void theReferenceEqualsTheBindings() {
        ActionMap map = ActionMap.defaults();

        List<Row> rows = ControlsOverlay.rows(map.bindings(), KeyReferenceTest::label);

        assertThat(rows).isEqualTo(expected(map));
        assertThat(rows).extracting(Row::nameKey).doesNotHaveDuplicates();
        for (Action a : Action.values()) {
            boolean bound = map.bindings().stream().anyMatch(b -> b.action() == a && b.rebindable());
            assertThat(rows.stream()
                            .anyMatch(r -> r.nameKey()
                                    .equals("key.strategos." + a.name().toLowerCase())))
                    .as(a + (bound ? " is rebindable: listed" : " is fixed: not listed"))
                    .isEqualTo(bound);
        }
    }

    @Test
    void aRebindChangesItsRowAndNoOther() {
        ActionMap map = ActionMap.defaults();
        List<Row> before = ControlsOverlay.rows(map.bindings(), KeyReferenceTest::label);

        ActionMap rebound = map.rebind(ActionContext.RTS, Action.BACK_TO_WORK, KeyCode.key(KeyCode.F2 + 100))
                .rebind(ActionContext.RTS, Action.SET_BOOKMARK_2, KeyCode.key('9'), Modifiers.ALT);
        List<Row> after = ControlsOverlay.rows(rebound.bindings(), KeyReferenceTest::label);

        assertThat(after).isEqualTo(expected(rebound));
        assertThat(after).hasSameSizeAs(before);
        List<Row> changed = after.stream().filter(r -> !before.contains(r)).toList();
        assertThat(changed)
                .containsExactlyInAnyOrder(
                        new Row("key.strategos.back_to_work", "KEY" + (KeyCode.F2 + 100)),
                        new Row("key.strategos.set_bookmark_2", "Alt+KEY57"));
    }
}
