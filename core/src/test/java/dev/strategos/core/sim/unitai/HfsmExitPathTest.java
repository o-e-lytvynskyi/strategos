package dev.strategos.core.sim.unitai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.sim.Arrived;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.UnitState;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * UNIT-02.3 (spec §7.2): a static check proves every state has an exit path. In the unit tree every leaf reaches IDLE
 * and can be entered from it. On synthetic trees the check finds a trap, a cycle with no way out and a leaf nothing
 * enters, counts an exit a parent declares, and checks a state with children through its leaves; and the builder
 * refuses every malformed tree.
 */
class HfsmExitPathTest {
    private static final StateId ROOT = new StateId(10, "root", null);
    private static final StateId HOME = new StateId(11, "home", UnitState.IDLE);
    private static final StateId A = new StateId(12, "a", UnitState.WALKING);
    private static final StateId B = new StateId(13, "b", UnitState.WALKING);
    private static final StateId GROUP = new StateId(14, "group", null);
    private static final StateId C = new StateId(15, "c", UnitState.WALKING);

    @Test
    void theUnitTreeHasNoProblem() {
        Hfsm<MatchState> tree = UnitAi.tree();

        assertThat(HfsmCheck.problems(tree)).isEmpty();
        assertThat(tree.initial()).isEqualTo(UnitAi.IDLE);
        assertThat(tree.leaves()).containsExactlyElementsOf(UnitAi.leaves());
        for (StateId leaf : tree.leaves()) {
            List<StateId> path = HfsmCheck.exitPath(tree, leaf);
            assertThat(path).as("the exit path of " + leaf).isNotEmpty();
            assertThat(path.get(0)).isEqualTo(leaf);
            assertThat(path.get(path.size() - 1)).isEqualTo(UnitAi.IDLE);
        }
        assertThat(HfsmCheck.exitPath(tree, UnitAi.WALKING)).containsExactly(UnitAi.WALKING, UnitAi.IDLE);
    }

    @Test
    void aTrapIsFound() {
        Hfsm<Object> tree = Hfsm.of(
                List.of(
                        StateDef.root(ROOT).initial(HOME).build(),
                        StateDef.child(HOME, ROOT).transitions(A).build(),
                        StateDef.child(A, ROOT).build()),
                HfsmExitPathTest::ignore);

        assertThat(HfsmCheck.problems(tree)).containsExactly("state a has no exit path to home");
        assertThat(HfsmCheck.exitPath(tree, A)).isEmpty();
    }

    @Test
    void aCycleWithNoWayOutIsFound() {
        Hfsm<Object> tree = Hfsm.of(
                List.of(
                        StateDef.root(ROOT).initial(HOME).build(),
                        StateDef.child(HOME, ROOT).transitions(A).build(),
                        StateDef.child(A, ROOT).transitions(B).build(),
                        StateDef.child(B, ROOT).transitions(A).build()),
                HfsmExitPathTest::ignore);

        assertThat(HfsmCheck.problems(tree))
                .containsExactly("state a has no exit path to home", "state b has no exit path to home");
    }

    @Test
    void anExitAParentDeclaresCounts() {
        Hfsm<Object> tree = Hfsm.of(
                List.of(
                        StateDef.root(ROOT).initial(HOME).transitions(HOME).build(),
                        StateDef.child(HOME, ROOT).transitions(A).build(),
                        StateDef.child(A, ROOT).build()),
                HfsmExitPathTest::ignore);

        assertThat(HfsmCheck.problems(tree)).isEmpty();
        assertThat(HfsmCheck.exitPath(tree, A)).containsExactly(A, HOME);
    }

    @Test
    void aStateWithChildrenIsCheckedThroughItsLeaves() {
        Hfsm<Object> tree = Hfsm.of(
                List.of(
                        StateDef.root(ROOT).initial(HOME).build(),
                        StateDef.child(HOME, ROOT).transitions(GROUP).build(),
                        StateDef.child(GROUP, ROOT).initial(C).build(),
                        StateDef.child(C, GROUP).transitions(HOME).build()),
                HfsmExitPathTest::ignore);

        assertThat(tree.leaves()).containsExactly(HOME, C);
        assertThat(HfsmCheck.problems(tree)).isEmpty();
        assertThat(HfsmCheck.exitPath(tree, GROUP))
                .as("through its initial leaf")
                .containsExactly(C, HOME);
        assertThat(HfsmCheck.next(tree, HOME))
                .as("a transition to a group ends in its initial leaf")
                .containsExactly(C);
    }

    @Test
    void aLeafNothingEntersIsFound() {
        Hfsm<Object> tree = Hfsm.of(
                List.of(
                        StateDef.root(ROOT).initial(HOME).build(),
                        StateDef.child(HOME, ROOT).build(),
                        StateDef.child(A, ROOT).transitions(HOME).build()),
                HfsmExitPathTest::ignore);

        assertThat(HfsmCheck.problems(tree)).containsExactly("state a cannot be entered from home");
    }

    @Test
    void malformedTreesAreRefused() {
        List<Supplier<Hfsm<Object>>> malformed = List.of(
                () -> Hfsm.of(List.of(), HfsmExitPathTest::ignore),
                () -> tree( // a code twice
                        StateDef.root(ROOT).initial(HOME).build(),
                        StateDef.child(HOME, ROOT).build(),
                        StateDef.child(new StateId(11, "other", UnitState.IDLE), ROOT)
                                .build()),
                () -> tree( // a name twice
                        StateDef.root(ROOT).initial(HOME).build(),
                        StateDef.child(HOME, ROOT).build(),
                        StateDef.child(new StateId(20, "home", UnitState.IDLE), ROOT)
                                .build()),
                () -> tree(
                        StateDef.root(ROOT).initial(HOME).build(),
                        StateDef.root(HOME).build()), // two roots
                () -> tree(StateDef.child(HOME, ROOT).build()), // no root
                () -> tree(
                        StateDef.root(ROOT).initial(HOME).build(),
                        StateDef.child(HOME, GROUP).build()), // parent
                () -> tree( // children but no initial child
                        StateDef.root(ROOT).build(), StateDef.child(HOME, ROOT).build()),
                () -> tree( // an initial child that is not a child
                        StateDef.root(ROOT).initial(C).build(),
                        StateDef.child(HOME, ROOT).build(),
                        StateDef.child(GROUP, ROOT).initial(C).build(),
                        StateDef.child(C, GROUP).build()),
                () -> tree( // a leaf that names an initial child
                        StateDef.root(ROOT).initial(HOME).build(),
                        StateDef.child(HOME, ROOT).initial(A).build()),
                () -> tree( // a leaf that reports no activity
                        StateDef.root(ROOT).initial(GROUP).build(),
                        StateDef.child(GROUP, ROOT).build()),
                () -> tree( // a transition to a state the tree lacks
                        StateDef.root(ROOT).initial(HOME).build(),
                        StateDef.child(HOME, ROOT).transitions(A).build()),
                () -> tree( // a message handled twice by one state
                        StateDef.root(ROOT).initial(HOME).build(),
                        StateDef.<Object>child(HOME, ROOT)
                                .on(Arrived.class, (ctx, m) -> {})
                                .on(Arrived.class, (ctx, m) -> {})
                                .build()),
                () -> tree( // no think period
                        StateDef.root(ROOT).initial(HOME).build(),
                        StateDef.child(HOME, ROOT).thinkEvery(0).build()));
        for (int k = 0; k < malformed.size(); k++) {
            Supplier<Hfsm<Object>> build = malformed.get(k);
            assertThatThrownBy(build::get).as("malformed tree " + k).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @SafeVarargs
    private static Hfsm<Object> tree(StateDef<Object>... states) {
        return Hfsm.of(List.of(states), HfsmExitPathTest::ignore);
    }

    private static void ignore(Object host, int handle, StateId from, StateId to) {}
}
