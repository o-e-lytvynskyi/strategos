package dev.strategos.core.client.hud;

import dev.strategos.core.client.select.ControlGroups;
import java.util.ArrayList;
import java.util.List;

/**
 * The control-group bar's view-model (UI-02 over UI-04): each group that has members, in key order 1–9 then 0, with
 * its size.
 */
public record GroupBar(List<Slot> slots) {
    /** Group {@code digit}, holding {@code count} units. */
    public record Slot(int digit, int count) {}

    public static GroupBar of(ControlGroups groups) {
        List<Slot> slots = new ArrayList<>();
        for (int k = 1; k <= 10; k++) {
            int digit = k % 10;
            int count = groups.members(digit).size();
            if (count > 0) {
                slots.add(new Slot(digit, count));
            }
        }
        return new GroupBar(List.copyOf(slots));
    }
}
