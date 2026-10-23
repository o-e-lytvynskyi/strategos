package dev.strategos.core.client.select;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** UI-04.1–3: set, add and recall; dead units leave; the groups outlive what the selection does meanwhile. */
class ControlGroupsTest {
    @Test
    void setAddAndRecall() {
        Selection selection = new Selection(0);
        ControlGroups groups = new ControlGroups();
        selection.set(List.of(3, 1));
        groups.set(1, selection);
        selection.set(List.of(7));
        groups.add(1, selection);
        groups.set(2, selection);

        selection.clear();
        groups.recall(1, selection);

        assertThat(selection.handles()).containsExactly(1, 3, 7);
        assertThat(groups.members(2)).containsExactly(7);
        assertThat(groups.members(0)).isEmpty();
    }

    @Test
    void deadUnitsLeaveTheirGroups() {
        Selection selection = new Selection(0);
        ControlGroups groups = new ControlGroups();
        selection.set(List.of(1, 2, 3));
        groups.set(4, selection);

        groups.prune(h -> h != 2);
        selection.prune(h -> h != 2);

        assertThat(groups.members(4)).containsExactly(1, 3);
        assertThat(selection.handles()).containsExactly(1, 3);
    }

    @Test
    void groupsOutliveTheSelectionsMeanwhile() {
        Selection selection = new Selection(0);
        ControlGroups groups = new ControlGroups();
        selection.set(List.of(5, 6));
        groups.set(3, selection);

        selection.clear(); // the first-person round trip leaves the client model as it is
        selection.set(List.of(9));

        groups.recall(3, selection);
        assertThat(selection.handles()).containsExactly(5, 6);
    }
}
