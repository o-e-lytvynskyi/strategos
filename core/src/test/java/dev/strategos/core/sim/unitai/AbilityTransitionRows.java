package dev.strategos.core.sim.unitai;

import static dev.strategos.core.sim.unitai.UnitTreeTransitionsTest.workerRow;
import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.sim.Arrived;
import dev.strategos.core.sim.BusMessage;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.Stuck;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.unitai.UnitTreeTransitionsTest.Unit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

/**
 * UNIT-02.1 for abilities (UNIT-10): the ABILITY state's rows of the unit tree, run by {@link
 * UnitTreeTransitionsTest} with its worker, which carries {@code poke}, a unit ability of 2 blocks' range (index 0 of the
 * set); the enemy stands 6 blocks off, out of range, until a row brings it near.
 */
final class AbilityTransitionRows {
    /** The ability the worker of these rows carries. */
    static final String POKE = "{\"poke\":{\"target\":\"unit\",\"range\":2,"
            + "\"effects\":[{\"kind\":\"damage\",\"params\":{\"type\":\"hack\",\"amount\":1}}]}}";

    private AbilityTransitionRows() {}

    /** The index of {@code poke} in the set the unit runs on: it follows the ids of every ability the set has. */
    private static int poke(Unit u) {
        int at = u.state.templates().abilityIndex("poke");
        assertThat(at).as("the rows' set carries poke").isNotNegative();
        return at;
    }

    /** The unit in the ABILITY state, its order on the enemy out of range. */
    static void start(Unit u) {
        u.place(u.enemy, 10, 8);
        u.state.orders().replace(u.index(), OrderKind.ABILITY, 10, 8, 0, poke(u), u.enemy);
        u.state.ai().set(u.index(), UnitAi.ABILITY);
    }

    /** A {@code poke} order on the enemy, put 6 blocks east of the unit, set as the ability rule sets it, then announced. */
    static BusMessage newAbility(Unit u) {
        double x = u.state.motion().x(u.index()) + 6; // wherever the unit stands: a gatherer is by its tree
        double z = u.state.motion().z(u.index());
        u.place(u.enemy, x, z);
        u.state.orders().replace(u.index(), OrderKind.ABILITY, x, z, 0, poke(u), u.enemy);
        return new OrderReceived(u.handle);
    }

    /** The ability order waiting inside the house, announced as the release does: the unit is out of the store. */
    private static BusMessage releasedToAbility(Unit u) {
        BusMessage message = newAbility(u);
        u.state.garrison().remove(u.index());
        return message;
    }

    private static void abilityKept(Unit u) {
        assertThat(u.state.orders().kind(u.index())).isEqualTo(OrderKind.ABILITY);
        assertThat(u.state.orders().target(u.index())).isEqualTo(u.enemy);
        assertThat(u.finished).isEmpty();
    }

    static Stream<Arguments> rows() {
        StateId idle = UnitAi.IDLE;
        StateId using = UnitAi.ABILITY;
        List<Arguments> out = new ArrayList<>();
        for (StateId from : List.of(
                idle,
                UnitAi.WALKING,
                UnitAi.WALKING_AND_FIGHTING,
                UnitAi.FLEEING,
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
                UnitAi.BURNING,
                UnitAi.GARRISON_APPROACHING,
                UnitAi.CAPTURE_APPROACHING,
                UnitAi.CAPTURING)) {
            out.add(workerRow(
                    "an ability order from " + from + " walks into range",
                    from,
                    AbilityTransitionRows::newAbility,
                    using,
                    AbilityTransitionRows::abilityKept));
        }
        out.add(workerRow(
                "let out with an ability order waiting walks into range",
                UnitAi.GARRISONED,
                AbilityTransitionRows::releasedToAbility,
                using,
                AbilityTransitionRows::abilityKept));
        out.add(workerRow(
                "a target that comes into range is used the ability on, and the order is done (a tick)",
                using,
                u -> {
                    u.place(u.enemy, 6, 8);
                    return null;
                },
                idle,
                UnitTreeTransitionsTest.orderFinished(false)));
        out.add(workerRow(
                "an arrival short of range fails the order",
                using,
                u -> new Arrived(u.handle),
                idle,
                UnitTreeTransitionsTest.orderFinished(true)));
        out.add(workerRow(
                "a target out of reach fails the order",
                using,
                u -> new Stuck(u.handle, Stuck.GIVE_UP),
                idle,
                UnitTreeTransitionsTest.orderFinished(true)));
        out.add(workerRow(
                "a target that died on the way fails the order (a tick)",
                using,
                u -> {
                    u.state.removeUnit(u.enemy);
                    return null;
                },
                idle,
                UnitTreeTransitionsTest.orderFinished(true)));
        out.add(workerRow(
                "a stop on the way idles",
                using,
                UnitTreeTransitionsTest::stop,
                idle,
                UnitTreeTransitionsTest::nothingElse));
        out.add(workerRow(
                "a move on the way",
                using,
                UnitTreeTransitionsTest::newMove,
                UnitAi.WALKING,
                UnitTreeTransitionsTest.kept(OrderKind.MOVE)));
        out.add(workerRow(
                "a flight on the way",
                using,
                FleeTransitionRows::newFlee,
                UnitAi.FLEEING,
                UnitTreeTransitionsTest.kept(OrderKind.FLEE)));
        out.add(workerRow(
                "an attack on the way",
                using,
                UnitTreeTransitionsTest::newAttack,
                UnitAi.APPROACHING,
                UnitTreeTransitionsTest.kept(OrderKind.ATTACK)));
        out.add(workerRow(
                "an attack-move on the way",
                using,
                UnitTreeTransitionsTest::newAttackMove,
                UnitAi.WALKING_AND_FIGHTING,
                UnitTreeTransitionsTest.kept(OrderKind.ATTACK_MOVE)));
        out.add(workerRow(
                "a follow on the way",
                using,
                UnitTreeTransitionsTest::newFollow,
                UnitAi.FOLLOW_WALKING,
                UnitTreeTransitionsTest.kept(OrderKind.FOLLOW)));
        out.add(workerRow(
                "a gather order on the way",
                using,
                GatherTransitionRows::newGather,
                UnitAi.GATHER_APPROACHING,
                UnitTreeTransitionsTest.kept(OrderKind.GATHER)));
        out.add(workerRow(
                "a build order on the way",
                using,
                BuildTransitionRows::newBuild,
                UnitAi.BUILD_APPROACHING,
                UnitTreeTransitionsTest.kept(OrderKind.BUILD)));
        out.add(workerRow(
                "a burn order on the way",
                using,
                BurnTransitionRows::newBurn,
                UnitAi.BURN_APPROACHING,
                UnitTreeTransitionsTest.kept(OrderKind.BURN)));
        out.add(workerRow(
                "a garrison order on the way",
                using,
                GarrisonTransitionRows::newGarrison,
                UnitAi.GARRISON_APPROACHING,
                UnitTreeTransitionsTest.kept(OrderKind.GARRISON)));
        out.add(workerRow(
                "a capture order on the way",
                using,
                CaptureTransitionRows::newCapture,
                UnitAi.CAPTURE_APPROACHING,
                UnitTreeTransitionsTest.kept(OrderKind.CAPTURE)));
        return out.stream();
    }
}
