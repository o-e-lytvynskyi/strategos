package dev.strategos.core.sim.unitai;

import static dev.strategos.core.sim.unitai.UnitTreeTransitionsTest.workerRow;
import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.sim.BusMessage;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.unitai.UnitTreeTransitionsTest.Unit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

/**
 * UNIT-04 / CMB-05 for FLEEING: a flight taken up from every state (the rows of {@link UnitTreeTransitionsTest} run
 * every row of one message on a unit in the start state), and a new order ending it. The unit is a worker, so the same
 * start states as the gather, build and burn rows are at hand.
 */
final class FleeTransitionRows {
    private FleeTransitionRows() {}

    /** A flight from the enemy put before the unit's orders, set as the retreat sets it, then announced. */
    static BusMessage newFlee(Unit u) {
        u.state.orders().pushFront(u.index(), OrderKind.FLEE, 2, 8, 0, 0, u.enemy);
        return new OrderReceived(u.handle);
    }

    /** The flight FLEEING's start state holds. */
    static void start(Unit u) {
        u.state.orders().replace(u.index(), OrderKind.FLEE, 2, 8, 0, 0, u.enemy);
        u.state.ai().set(u.index(), UnitAi.FLEEING);
    }

    private static Consumer<Unit> fleeing() {
        return u -> {
            assertThat(u.state.orders().kind(u.index())).isEqualTo(OrderKind.FLEE);
            assertThat(u.state.orders().target(u.index())).isEqualTo(u.enemy);
        };
    }

    static Stream<Arguments> rows() {
        List<Arguments> out = new ArrayList<>();
        for (StateId from : List.of(
                UnitAi.IDLE,
                UnitAi.WALKING,
                UnitAi.WALKING_AND_FIGHTING,
                UnitAi.APPROACHING,
                UnitAi.ATTACKING,
                UnitAi.CHASING,
                UnitAi.FOLLOW_WALKING,
                UnitAi.FOLLOW_WAITING,
                UnitAi.GATHER_APPROACHING,
                UnitAi.GATHERING,
                UnitAi.GATHER_RETURNING,
                UnitAi.BUILD_APPROACHING,
                UnitAi.BUILDING,
                UnitAi.BURN_APPROACHING,
                UnitAi.BURNING)) {
            out.add(workerRow("a flight from " + from, from, FleeTransitionRows::newFlee, UnitAi.FLEEING, fleeing()));
        }
        StateId from = UnitAi.FLEEING;
        out.add(workerRow(
                "a stop while fleeing idles",
                from,
                UnitTreeTransitionsTest::stop,
                UnitAi.IDLE,
                UnitTreeTransitionsTest::nothingElse));
        out.add(workerRow(
                "a move while fleeing",
                from,
                UnitTreeTransitionsTest::newMove,
                UnitAi.WALKING,
                UnitTreeTransitionsTest.kept(OrderKind.MOVE)));
        out.add(workerRow(
                "an attack while fleeing",
                from,
                UnitTreeTransitionsTest::newAttack,
                UnitAi.APPROACHING,
                UnitTreeTransitionsTest.kept(OrderKind.ATTACK)));
        out.add(workerRow(
                "an attack-move while fleeing",
                from,
                UnitTreeTransitionsTest::newAttackMove,
                UnitAi.WALKING_AND_FIGHTING,
                UnitTreeTransitionsTest.kept(OrderKind.ATTACK_MOVE)));
        out.add(workerRow(
                "a follow while fleeing",
                from,
                UnitTreeTransitionsTest::newFollow,
                UnitAi.FOLLOW_WALKING,
                UnitTreeTransitionsTest.kept(OrderKind.FOLLOW)));
        out.add(workerRow(
                "a gather order while fleeing",
                from,
                GatherTransitionRows::newGather,
                UnitAi.GATHER_APPROACHING,
                UnitTreeTransitionsTest.kept(OrderKind.GATHER)));
        out.add(workerRow(
                "a build order while fleeing",
                from,
                BuildTransitionRows::newBuild,
                UnitAi.BUILD_APPROACHING,
                UnitTreeTransitionsTest.kept(OrderKind.BUILD)));
        out.add(workerRow(
                "a burn order while fleeing",
                from,
                BurnTransitionRows::newBurn,
                UnitAi.BURN_APPROACHING,
                UnitTreeTransitionsTest.kept(OrderKind.BURN)));
        return out.stream();
    }
}
