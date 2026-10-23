package dev.strategos.core.client.select;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** UI-03.1: a box takes the own units whose feet or head project inside it, in handle order, partly visible ones too. */
class BoxSelectTest {
    @Test
    void aBoxTakesOwnUnitsByFeetOrHeadInHandleOrder() {
        List<Seen> seen = new ArrayList<>();
        seen.add(new Seen(30, 0, 1, 100, 100, 100, 80));
        seen.add(new Seen(10, 0, 1, 150, 120, 150, 100));
        seen.add(new Seen(20, 1, 1, 120, 110, 120, 90)); // an enemy inside
        seen.add(new Seen(40, 0, 1, 100, 210, 100, 190)); // only its head inside: partly visible
        seen.add(new Seen(50, 0, 1, 400, 400, 400, 380)); // outside
        seen.add(new Seen(60, 0, 1, Double.NaN, Double.NaN, Double.NaN, Double.NaN)); // behind the camera
        Selection selection = new Selection(0);

        selection.box(seen, 90, 70, 200, 200, false);

        assertThat(selection.handles()).containsExactly(10, 30, 40);
    }

    @Test
    void shiftAddsABoxToTheSelection() {
        List<Seen> seen = List.of(new Seen(1, 0, 1, 10, 10, 10, 5), new Seen(2, 0, 1, 300, 300, 300, 290));
        Selection selection = new Selection(0);
        selection.box(seen, 0, 0, 50, 50, false);

        selection.box(seen, 250, 250, 350, 350, true);

        assertThat(selection.handles()).containsExactly(1, 2);
    }
}
