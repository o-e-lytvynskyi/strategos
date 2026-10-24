package dev.strategos.core.client.select;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.IntPredicate;

/**
 * Control groups 0–9 (UI-04, 04 §13.4): Ctrl and a digit sets the group to the selection, Shift and a digit adds the
 * selection to it, the digit alone recalls it, and a double tap centres the camera on it. Units that die leave their
 * groups. Groups stay on the client for the session, so the first-person round trip keeps them.
 */
public final class ControlGroups {
    private final List<TreeSet<Integer>> groups = new ArrayList<>();

    public ControlGroups() {
        for (int k = 0; k < 10; k++) {
            groups.add(new TreeSet<>());
        }
    }

    public void set(int group, Selection selection) {
        TreeSet<Integer> g = groups.get(group);
        g.clear();
        g.addAll(selection.handles());
    }

    public void add(int group, Selection selection) {
        groups.get(group).addAll(selection.handles());
    }

    public void recall(int group, Selection selection) {
        selection.set(members(group));
    }

    /** The group's handles, ascending. */
    public List<Integer> members(int group) {
        return List.copyOf(groups.get(group));
    }

    /** Every group's handles, 0 to 9 (MATCH-04: kept per match across a resume). */
    public List<List<Integer>> snapshot() {
        List<List<Integer>> out = new ArrayList<>(groups.size());
        for (TreeSet<Integer> g : groups) {
            out.add(List.copyOf(g));
        }
        return out;
    }

    /** The groups as {@code saved} held them; groups it lacks are empty. */
    public void restore(List<List<Integer>> saved) {
        for (int k = 0; k < groups.size(); k++) {
            groups.get(k).clear();
            if (k < saved.size()) {
                groups.get(k).addAll(saved.get(k));
            }
        }
    }

    /** Drops the handles {@code alive} no longer holds from every group. */
    public void prune(IntPredicate alive) {
        for (TreeSet<Integer> g : groups) {
            g.removeIf(h -> !alive.test(h));
        }
    }
}
