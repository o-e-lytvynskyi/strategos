package dev.strategos.core.sim.unitai;

import static dev.strategos.core.sim.unitai.UnitTreeTransitionsTest.workerRow;
import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.sim.Arrived;
import dev.strategos.core.sim.BusMessage;
import dev.strategos.core.sim.OrderFinished;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.Stuck;
import dev.strategos.core.sim.garrison.Garrisons;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.unitai.UnitTreeTransitionsTest.Unit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.provider.Arguments;

/**
 * UNIT-02.1 for garrisons (BLD-16): the GARRISON states' rows of the unit tree, run by {@link UnitTreeTransitionsTest}
 * with its worker; the faction's finished civic centre at (20, 0) is the building the unit is sent into, 15 blocks from
 * where it stands.
 */
final class GarrisonTransitionRows {
    private GarrisonTransitionRows() {}

    /** The unit in {@code from}: sent to the centre, or inside it with one move waiting behind. */
    static void start(Unit u, StateId from) {
        int i = u.index();
        if (from.equals(UnitAi.GARRISON_APPROACHING)) {
            u.state.orders().replace(i, OrderKind.GARRISON, 20.5, 11.5, 0, 0, u.dropsite);
            u.state.ai().set(i, from);
        } else {
            u.state.orders().replace(i, OrderKind.MOVE, 8, 8, 0, 0);
            u.state.garrison().add(i, u.handle, u.dropsite);
            u.state.ai().set(i, from);
        }
    }

    /** A garrison order into the centre, set as the garrison rule sets it, then announced. */
    static BusMessage newGarrison(Unit u) {
        u.state.orders().replace(u.index(), OrderKind.GARRISON, 20.5, 11.5, 0, 0, u.dropsite);
        return new OrderReceived(u.handle);
    }

    /**
     * What let out of the centre does for the order waiting in it: the order set, the unit out of the store, then
     * announced as the release does.
     */
    private static Function<Unit, @Nullable BusMessage> released(Function<Unit, @Nullable BusMessage> order) {
        return u -> {
            @Nullable BusMessage message = order.apply(u);
            u.state.garrison().remove(u.index());
            return message;
        };
    }

    private static Consumer<Unit> heading(int kind) {
        return u -> assertThat(u.state.orders().kind(u.index())).isEqualTo(kind);
    }

    private static BusMessage nothingWaiting(Unit u) {
        u.state.orders().clear(u.index());
        return new OrderReceived(u.handle);
    }

    static Stream<Arguments> rows() {
        StateId toDoor = UnitAi.GARRISON_APPROACHING;
        StateId inside = UnitAi.GARRISONED;
        List<Arguments> out = new ArrayList<>();
        for (StateId from : List.of(
                UnitAi.IDLE,
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
                UnitAi.BURNING)) {
            out.add(workerRow(
                    "a garrison order from " + from + " walks to the door",
                    from,
                    GarrisonTransitionRows::newGarrison,
                    toDoor,
                    heading(OrderKind.GARRISON)));
        }
        out.add(workerRow(
                "a move on the way to the door walks",
                toDoor,
                UnitTreeTransitionsTest::newMove,
                UnitAi.WALKING,
                heading(OrderKind.MOVE)));
        out.add(workerRow(
                "a flight on the way to the door runs",
                toDoor,
                FleeTransitionRows::newFlee,
                UnitAi.FLEEING,
                heading(OrderKind.FLEE)));
        out.add(workerRow(
                "an attack on the way to the door closes in",
                toDoor,
                UnitTreeTransitionsTest::newAttack,
                UnitAi.APPROACHING,
                heading(OrderKind.ATTACK)));
        out.add(workerRow(
                "an attack-move on the way to the door walks and fights",
                toDoor,
                UnitTreeTransitionsTest::newAttackMove,
                UnitAi.WALKING_AND_FIGHTING,
                heading(OrderKind.ATTACK_MOVE)));
        out.add(workerRow(
                "a follow on the way to the door follows",
                toDoor,
                UnitTreeTransitionsTest::newFollow,
                UnitAi.FOLLOW_WALKING,
                heading(OrderKind.FOLLOW)));
        out.add(workerRow(
                "a gather order on the way to the door goes to the tree",
                toDoor,
                GatherTransitionRows::newGather,
                UnitAi.GATHER_APPROACHING,
                heading(OrderKind.GATHER)));
        out.add(workerRow(
                "a build order on the way to the door goes to the site",
                toDoor,
                BuildTransitionRows::newBuild,
                UnitAi.BUILD_APPROACHING,
                heading(OrderKind.BUILD)));
        out.add(workerRow(
                "a burn order on the way to the door goes to the house",
                toDoor,
                BurnTransitionRows::newBurn,
                UnitAi.BURN_APPROACHING,
                heading(OrderKind.BURN)));
        out.add(workerRow(
                "a stop on the way to the door idles",
                toDoor,
                UnitTreeTransitionsTest::stop,
                UnitAi.IDLE,
                UnitTreeTransitionsTest::nothingElse));
        out.add(workerRow(
                "the stuck ladder's last rung fails the garrison order",
                toDoor,
                u -> new Stuck(u.handle, Stuck.GIVE_UP),
                UnitAi.IDLE,
                UnitTreeTransitionsTest.orderFinished(true)));
        out.add(workerRow(
                "arriving within reach of the door goes in, the order done",
                toDoor,
                u -> {
                    u.place(u.handle, 20.5, 11.5);
                    return new Arrived(u.handle);
                },
                inside,
                u -> {
                    assertThat(u.finished).containsExactly(new OrderFinished(u.handle, false));
                    assertThat(u.state.garrison().inside(u.index())).isTrue();
                    assertThat(u.state.garrison().buildingOf(u.index())).isEqualTo(u.dropsite);
                    assertThat(u.state.orders().kind(u.index())).isEqualTo(OrderKind.NONE);
                }));
        out.add(workerRow("arriving short of the door walks on", toDoor, u -> new Arrived(u.handle), toDoor, u -> {
            assertThat(u.state.garrison().inside(u.index())).isFalse();
            assertThat(Garrisons.REACH).isLessThan(15);
        }));
        out.add(workerRow(
                "an order while inside waits for the way out", inside, UnitTreeTransitionsTest::newMove, inside, u -> {
                    assertThat(u.state.garrison().inside(u.index())).isTrue();
                    assertThat(u.state.orders().kind(u.index())).isEqualTo(OrderKind.MOVE);
                }));
        out.add(workerRow(
                "a stop while inside leaves it inside",
                inside,
                UnitTreeTransitionsTest::stop,
                inside,
                u -> assertThat(u.state.garrison().inside(u.index())).isTrue()));
        out.add(workerRow(
                "let out with nothing waiting idles",
                inside,
                released(GarrisonTransitionRows::nothingWaiting),
                UnitAi.IDLE,
                heading(OrderKind.NONE)));
        out.add(workerRow(
                "let out with a move waiting walks",
                inside,
                released(UnitTreeTransitionsTest::newMove),
                UnitAi.WALKING,
                heading(OrderKind.MOVE)));
        out.add(workerRow(
                "let out with a flight waiting runs",
                inside,
                released(FleeTransitionRows::newFlee),
                UnitAi.FLEEING,
                heading(OrderKind.FLEE)));
        out.add(workerRow(
                "let out with an attack waiting closes in",
                inside,
                released(UnitTreeTransitionsTest::newAttack),
                UnitAi.APPROACHING,
                heading(OrderKind.ATTACK)));
        out.add(workerRow(
                "let out with an attack-move waiting walks and fights",
                inside,
                released(UnitTreeTransitionsTest::newAttackMove),
                UnitAi.WALKING_AND_FIGHTING,
                heading(OrderKind.ATTACK_MOVE)));
        out.add(workerRow(
                "let out with a follow waiting follows",
                inside,
                released(UnitTreeTransitionsTest::newFollow),
                UnitAi.FOLLOW_WALKING,
                heading(OrderKind.FOLLOW)));
        out.add(workerRow(
                "let out with a gather order waiting goes to the tree",
                inside,
                released(GatherTransitionRows::newGather),
                UnitAi.GATHER_APPROACHING,
                heading(OrderKind.GATHER)));
        out.add(workerRow(
                "let out with a build order waiting goes to the site",
                inside,
                released(BuildTransitionRows::newBuild),
                UnitAi.BUILD_APPROACHING,
                heading(OrderKind.BUILD)));
        out.add(workerRow(
                "let out with a burn order waiting goes to the house",
                inside,
                released(BurnTransitionRows::newBurn),
                UnitAi.BURN_APPROACHING,
                heading(OrderKind.BURN)));
        out.add(workerRow(
                "let out with another garrison order waiting walks to its door",
                inside,
                released(GarrisonTransitionRows::newGarrison),
                toDoor,
                heading(OrderKind.GARRISON)));
        return out.stream();
    }
}
