package dev.strategos.core.client.hud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.client.hud.ControlsOverlay.Row;
import dev.strategos.core.client.input.Action;
import dev.strategos.core.client.input.Binding;
import dev.strategos.core.client.input.DefaultBindings;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** UI-14.2 in L1: the overlay lists the rebindable keys on their current keys, a rebind included. */
class ControlsOverlayTest {
    private static Map<Action, String> defaults() {
        Map<Action, String> keys = new HashMap<>();
        for (Binding b : DefaultBindings.all()) {
            keys.put(b.action(), b.key().code() + "");
        }
        return keys;
    }

    @Test
    void everyRebindableBindingHasARowOnItsKey() {
        Map<Action, String> keys = defaults();
        List<Row> rows = ControlsOverlay.rows(DefaultBindings.all(), b -> keys.get(b.action()));
        long rebindable =
                DefaultBindings.all().stream().filter(Binding::rebindable).count();
        assertThat(rows).hasSize((int) rebindable);
        assertThat(rows).contains(new Row("key.strategos.controls", "75")); // K, the overlay's own key
        assertThat(rows).contains(new Row("key.strategos.jump_to_alert", "74"));
        assertThat(rows)
                .extracting(Row::nameKey)
                .as("the fixed mouse and escape are not key mappings")
                .doesNotContain("key.strategos.select", "key.strategos.cancel")
                .doesNotHaveDuplicates();
    }

    @Test
    void aRebindShowsItsNewKey() {
        Map<Action, String> keys = defaults();
        keys.put(Action.JUMP_TO_ALERT, "Ctrl + F");
        keys.put(Action.CONTROLS, "F1");
        List<Row> rows = ControlsOverlay.rows(DefaultBindings.all(), b -> keys.get(b.action()));
        assertThat(rows).contains(new Row("key.strategos.jump_to_alert", "Ctrl + F"));
        assertThat(rows).contains(new Row("key.strategos.controls", "F1"));
        assertThat(rows).doesNotContain(new Row("key.strategos.jump_to_alert", "74"));
    }

    @Test
    void aBindingWithoutAMappingHasNoRow() {
        List<Row> rows = ControlsOverlay.rows(DefaultBindings.all(), b -> b.action() == Action.STOP ? "H" : null);
        assertThat(rows).containsExactly(new Row("key.strategos.stop", "H"));
    }
}
