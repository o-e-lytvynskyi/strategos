package dev.strategos.core.sim.unitai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * UNIT-02.3's static check, over the transitions a tree declares: every state has an exit path, a chain of transitions
 * to the tree's initial leaf (IDLE, for units), and every state can be entered from there. In a leaf the transitions of
 * the leaf and of each of its ancestors apply, since their handlers and thinks run for it, and a transition to a state
 * with children ends in its initial leaf; a state with children is checked through its leaves, the states a unit is
 * ever in. The check sees declarations only: {@link Hfsm.Context#go} refuses any transition a state does not declare.
 */
public final class HfsmCheck {
    private static final int UNSEEN = -2;
    private static final int START = -1;

    private HfsmCheck() {}

    /** One line per problem, leaves in the tree's order; empty when every state has an exit path and can be entered. */
    public static List<String> problems(Hfsm<?> tree) {
        StateId home = tree.initial();
        List<String> problems = new ArrayList<>();
        for (StateId leaf : tree.leaves()) {
            if (exitPath(tree, leaf).isEmpty()) {
                problems.add("state " + leaf + " has no exit path to " + home);
            }
        }
        int[] reached = search(tree, home);
        List<StateId> leaves = tree.leaves();
        for (int k = 0; k < leaves.size(); k++) {
            if (reached[k] == UNSEEN) {
                problems.add("state " + leaves.get(k) + " cannot be entered from " + home);
            }
        }
        return problems;
    }

    /**
     * The shortest chain of leaves from {@code state} (its initial leaf, for a state with children) to the initial leaf,
     * both ends included, the earlier declared transition first on a tie; empty when there is none.
     */
    public static List<StateId> exitPath(Hfsm<?> tree, StateId state) {
        List<StateId> leaves = tree.leaves();
        int[] previous = search(tree, tree.initialLeaf(state));
        int home = leaves.indexOf(tree.initial());
        List<StateId> path = new ArrayList<>();
        if (previous[home] == UNSEEN) {
            return path;
        }
        for (int k = home; k != START; k = previous[k]) {
            path.add(0, leaves.get(k));
        }
        return path;
    }

    /** The leaves a unit in {@code leaf} may go to next, in the order declared from the root down. */
    public static List<StateId> next(Hfsm<?> tree, StateId leaf) {
        List<StateId> next = new ArrayList<>();
        for (StateId state : tree.ancestry(leaf)) {
            for (StateId target : tree.transitions(state)) {
                StateId to = tree.initialLeaf(target);
                if (!next.contains(to)) {
                    next.add(to);
                }
            }
        }
        return next;
    }

    /** A breadth-first search over the leaves from {@code start}: each leaf's predecessor, START, or UNSEEN. */
    private static int[] search(Hfsm<?> tree, StateId start) {
        List<StateId> leaves = tree.leaves();
        int[] previous = new int[leaves.size()];
        Arrays.fill(previous, UNSEEN);
        int[] queue = new int[leaves.size()];
        int head = 0;
        int tail = 0;
        int first = leaves.indexOf(start);
        previous[first] = START;
        queue[tail++] = first;
        while (head < tail) {
            int at = queue[head++];
            for (StateId to : next(tree, leaves.get(at))) {
                int k = leaves.indexOf(to);
                if (previous[k] == UNSEEN) {
                    previous[k] = at;
                    queue[tail++] = k;
                }
            }
        }
        return previous;
    }
}
