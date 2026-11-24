package dev.strategos.core.sim.unitai;

import static dev.strategos.core.sim.unitai.UnitTreeTransitionsTest.workerRow;
import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.sim.Arrived;
import dev.strategos.core.sim.BusMessage;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.Stuck;
import dev.strategos.core.sim.build.BuildingLife;
import dev.strategos.core.sim.build.Placing;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.unitai.UnitTreeTransitionsTest.Unit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

/**
 * UNIT-02.1 for capture (CMB-10): the CAPTURE states' rows of the unit tree, run by {@link UnitTreeTransitionsTest}
 * with its worker (capture strength 1); the enemy's finished house stands at (25, 12), 21 blocks from the unit.
 */
final class CaptureTransitionRows {
    private CaptureTransitionRows() {}

    /** The place round the house, the first of its ring. */
    private static int cell(Unit u) {
        return Placing.slots(u.state, u.state.footprintOf(u.enemyHouse))[0];
    }

    /** The unit in {@code from}: its capture order and place; capturing, standing on it. */
    static void start(Unit u, StateId from) {
        int i = u.index();
        int cell = cell(u);
        u.state.orders().replace(i, OrderKind.CAPTURE, (cell >>> 16) + 0.5, (cell & 0xFFFF) + 0.5, 0, 1, u.enemyHouse);
        u.state.ai().set(i, from);
        if (from.equals(UnitAi.CAPTURING)) {
            u.place(u.handle, (cell >>> 16) + 0.5, (cell & 0xFFFF) + 0.5);
        }
    }

    /** A capture order on the house, set as the capture rule sets it, then announced. */
    static BusMessage newCapture(Unit u) {
        u.state.orders().replace(u.index(), OrderKind.CAPTURE, 27.5, 14.5, 0, 0, u.enemyHouse);
        return new OrderReceived(u.handle);
    }

    /** The capture order waiting inside the house, announced as the release does: the unit is out of the store. */
    private static BusMessage releasedToCapture(Unit u) {
        BusMessage message = newCapture(u);
        u.state.garrison().remove(u.index());
        return message;
    }

    private static void captureOrderKept(Unit u) {
        assertThat(u.state.orders().kind(u.index())).isEqualTo(OrderKind.CAPTURE);
        assertThat(u.state.orders().target(u.index())).isEqualTo(u.enemyHouse);
    }

    static Stream<Arguments> rows() {
        StateId idle = UnitAi.IDLE;
        StateId toHouse = UnitAi.CAPTURE_APPROACHING;
        StateId capturing = UnitAi.CAPTURING;
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
                capturing)) {
            out.add(workerRow(
                    "a capture order from " + from + " walks to a place",
                    from,
                    CaptureTransitionRows::newCapture,
                    toHouse,
                    CaptureTransitionRows::captureOrderKept));
        }
        out.add(workerRow(
                "let out with a capture order waiting walks to a place",
                UnitAi.GARRISONED,
                CaptureTransitionRows::releasedToCapture,
                toHouse,
                CaptureTransitionRows::captureOrderKept));
        out.add(workerRow(
                "arriving at the place captures",
                toHouse,
                u -> {
                    u.place(
                            u.handle,
                            u.state.orders().targetX(u.index()),
                            u.state.orders().targetZ(u.index()));
                    return new Arrived(u.handle);
                },
                capturing,
                CaptureTransitionRows::captureOrderKept));
        out.add(workerRow(
                "arriving short of the place walks on",
                toHouse,
                u -> new Arrived(u.handle),
                toHouse,
                CaptureTransitionRows::captureOrderKept));
        out.add(workerRow(
                "a house the capturer cannot reach fails the order",
                toHouse,
                u -> new Stuck(u.handle, Stuck.GIVE_UP),
                idle,
                UnitTreeTransitionsTest.orderFinished(true)));
        out.add(workerRow(
                "a house that fell while capturing ends the order (a tick)",
                capturing,
                u -> {
                    u.state.destroyBuilding(u.enemyHouse);
                    return null;
                },
                idle,
                UnitTreeTransitionsTest.orderFinished(false)));
        out.add(workerRow(
                "a house that changed hands to the unit's faction ends the order (a tick)",
                capturing,
                u -> {
                    BuildingLife.transfer(u.state, u.enemyHouse, 0);
                    return null;
                },
                idle,
                UnitTreeTransitionsTest.orderFinished(false)));
        out.add(workerRow(
                "a capturer pushed out of reach walks back (a tick)",
                capturing,
                u -> {
                    u.place(u.handle, 4, 8);
                    return null;
                },
                toHouse,
                CaptureTransitionRows::captureOrderKept));
        for (StateId from : List.of(toHouse, capturing)) {
            String at = from.equals(toHouse) ? "on the way to the house" : "while capturing";
            out.add(workerRow(
                    "a stop " + at + " idles",
                    from,
                    UnitTreeTransitionsTest::stop,
                    idle,
                    UnitTreeTransitionsTest::nothingElse));
            out.add(workerRow(
                    "a move " + at,
                    from,
                    UnitTreeTransitionsTest::newMove,
                    UnitAi.WALKING,
                    UnitTreeTransitionsTest.kept(OrderKind.MOVE)));
            out.add(workerRow(
                    "a flight " + at,
                    from,
                    FleeTransitionRows::newFlee,
                    UnitAi.FLEEING,
                    UnitTreeTransitionsTest.kept(OrderKind.FLEE)));
            out.add(workerRow(
                    "an attack " + at,
                    from,
                    UnitTreeTransitionsTest::newAttack,
                    UnitAi.APPROACHING,
                    UnitTreeTransitionsTest.kept(OrderKind.ATTACK)));
            out.add(workerRow(
                    "an attack-move " + at,
                    from,
                    UnitTreeTransitionsTest::newAttackMove,
                    UnitAi.WALKING_AND_FIGHTING,
                    UnitTreeTransitionsTest.kept(OrderKind.ATTACK_MOVE)));
            out.add(workerRow(
                    "a follow " + at,
                    from,
                    UnitTreeTransitionsTest::newFollow,
                    UnitAi.FOLLOW_WALKING,
                    UnitTreeTransitionsTest.kept(OrderKind.FOLLOW)));
            out.add(workerRow(
                    "a gather order " + at,
                    from,
                    GatherTransitionRows::newGather,
                    UnitAi.GATHER_APPROACHING,
                    UnitTreeTransitionsTest.kept(OrderKind.GATHER)));
            out.add(workerRow(
                    "a build order " + at,
                    from,
                    BuildTransitionRows::newBuild,
                    UnitAi.BUILD_APPROACHING,
                    UnitTreeTransitionsTest.kept(OrderKind.BUILD)));
            out.add(workerRow(
                    "a burn order " + at,
                    from,
                    BurnTransitionRows::newBurn,
                    UnitAi.BURN_APPROACHING,
                    UnitTreeTransitionsTest.kept(OrderKind.BURN)));
            out.add(workerRow(
                    "a garrison order " + at,
                    from,
                    GarrisonTransitionRows::newGarrison,
                    UnitAi.GARRISON_APPROACHING,
                    UnitTreeTransitionsTest.kept(OrderKind.GARRISON)));
        }
        return out.stream();
    }
}
