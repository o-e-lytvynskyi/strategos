package dev.strategos.core.client.select;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** UI-03.2: an enemy is inspected but never selected, a selection never exceeds 300, Shift toggles, a double click takes the type. */
class SelectionRulesTest {
    @Test
    void anEnemyIsInspectedNeverSelected() {
        Selection selection = new Selection(0);
        selection.click(new Seen(5, 0, 1, 0, 0, 0, 0), false);

        selection.click(new Seen(9, 1, 1, 0, 0, 0, 0), true);

        assertThat(selection.handles()).containsExactly(5);
        assertThat(selection.inspected()).isEqualTo(9);
        selection.click(new Seen(9, 1, 1, 0, 0, 0, 0), false);
        assertThat(selection.handles()).isEmpty();
    }

    @Test
    void aSelectionHoldsAtMost300() {
        List<Seen> seen = new ArrayList<>();
        for (int h = 1; h <= 400; h++) {
            seen.add(new Seen(h, 0, 1, 10, 10, 10, 5));
        }
        Selection selection = new Selection(0);

        selection.box(seen, 0, 0, 20, 20, false);

        assertThat(selection.handles()).hasSize(Selection.MOST).startsWith(1).endsWith(300);
        selection.click(new Seen(999, 0, 1, 0, 0, 0, 0), true);
        assertThat(selection.handles()).hasSize(Selection.MOST).doesNotContain(999);
    }

    @Test
    void shiftTogglesAndADoubleClickTakesTheTemplate() {
        Seen a = new Seen(1, 0, 7, 0, 0, 0, 0);
        Seen b = new Seen(2, 0, 7, 0, 0, 0, 0);
        Seen c = new Seen(3, 0, 8, 0, 0, 0, 0);
        Seen enemy = new Seen(4, 1, 7, 0, 0, 0, 0);
        Selection selection = new Selection(0);
        selection.click(a, false);
        selection.click(c, true);
        selection.click(a, true);
        assertThat(selection.handles()).containsExactly(3);

        selection.allOfType(List.of(a, b, c, enemy), a, false);

        assertThat(selection.handles()).containsExactly(1, 2);
        selection.click(null, false);
        assertThat(selection.handles()).isEmpty();
    }
}
