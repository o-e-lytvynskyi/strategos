package dev.strategos.core.client.hud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.client.select.ControlGroups;
import dev.strategos.core.client.select.Selection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** UI-02's first view-models: the selection summary and the control-group bar. */
class HudViewModelTest {
    private static final Map<Integer, Integer> TEMPLATES = Map.of(1, 0, 2, 1, 3, 1, 4, 1, 5, 0, 6, 2);

    @Test
    void theSummaryCountsEachTypeLargestFirst() {
        SelectionSummary s = SelectionSummary.of(
                List.of(1, 2, 3, 4, 5, 6, 7), h -> TEMPLATES.getOrDefault(h, -1), t -> "t" + t, null);

        assertThat(s.total()).isEqualTo(7);
        assertThat(s.types())
                .containsExactly(
                        new SelectionSummary.TypeCount(1, "t1", 3),
                        new SelectionSummary.TypeCount(0, "t0", 2),
                        new SelectionSummary.TypeCount(-1, "?", 1),
                        new SelectionSummary.TypeCount(2, "t2", 1));
        assertThat(s.inspected()).isNull();
    }

    @Test
    void anEmptySelectionShowsTheInspectedUnit() {
        assertThat(SelectionSummary.of(List.of(), h -> 0, t -> "t", null)).isEqualTo(SelectionSummary.EMPTY);
        assertThat(SelectionSummary.of(List.of(), h -> 0, t -> "t", "Debug walker")
                        .inspected())
                .isEqualTo("Debug walker");
    }

    @Test
    void theBarListsGroupsWithMembersInKeyOrder() {
        Selection selection = new Selection(0);
        ControlGroups groups = new ControlGroups();
        selection.set(List.of(1, 2));
        groups.set(0, selection);
        selection.set(List.of(3));
        groups.set(2, selection);

        assertThat(GroupBar.of(groups).slots()).containsExactly(new GroupBar.Slot(2, 1), new GroupBar.Slot(0, 2));
    }
}
