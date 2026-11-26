package dev.strategos.core.sim.unitai;

import static dev.strategos.core.sim.unitai.UnitTreeTransitionsTest.workerRow;
import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.FormationShape;
import dev.strategos.core.sim.Arrived;
import dev.strategos.core.sim.BusMessage;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.Stuck;
import dev.strategos.core.sim.formation.FormationController;
import dev.strategos.core.sim.formation.Formations;
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
 * UNIT-02.1 for formations (ORD-06): the FORMATION_MEMBER states' rows of the unit tree, run by {@link
 * UnitTreeTransitionsTest} with its worker. The unit is the only member of a formation marching to (60, 8); a row
 * that needs the formation to move steps a tick.
 */
final class FormationTransitionRows {
    private FormationTransitionRows() {}

    /** A formation of the unit alone, to (60, 8); the unit's order in it is set as the formation command sets it. */
    private static FormationController form(Unit u) {
        FormationController c = Formations.form(u.state, FormationShape.BOX, new int[] {u.handle}, 60, 8);
        assertThat(c).isNotNull();
        u.state.orders().replace(u.index(), OrderKind.FORMATION, c.slotX(0), c.slotZ(0), 0, 0, c.id());
        return c;
    }

    /** The unit in the state {@code from}, as a save restores it: its formation order, its formation. */
    static void start(Unit u, StateId from) {
        form(u);
        u.state.ai().set(u.index(), from);
    }

    /** A formation order, set as the formation rule sets it, then announced. */
    static BusMessage newFormation(Unit u) {
        form(u);
        return new OrderReceived(u.handle);
    }

    /** The formation order waiting inside the house, announced as the release does: the unit is out of the store. */
    private static BusMessage releasedToFormation(Unit u) {
        BusMessage message = newFormation(u);
        u.state.garrison().remove(u.index());
        return message;
    }

    private static void formationKept(Unit u) {
        assertThat(u.state.orders().kind(u.index())).isEqualTo(OrderKind.FORMATION);
        assertThat(u.state.play().formations().byId(u.state.orders().target(u.index())))
                .isNotNull();
        assertThat(u.finished).isEmpty();
    }

    /** A formation of the unit alone with nowhere to go: it holds at once, with the unit on its place; then a tick. */
    private static BusMessage standHere(Unit u) {
        FormationController c = Formations.form(
                u.state,
                FormationShape.BOX,
                new int[] {u.handle},
                u.state.motion().x(u.index()),
                u.state.motion().z(u.index()));
        assertThat(c).isNotNull();
        u.state.orders().replace(u.index(), OrderKind.FORMATION, c.slotX(0), c.slotZ(0), 0, 0, c.id());
        return null;
    }

    /** The unit is pushed three blocks off its place. */
    private static BusMessage pushed(Unit u) {
        u.place(u.handle, u.state.motion().x(u.index()) + 3, u.state.motion().z(u.index()));
        return null;
    }

    static Stream<Arguments> rows() {
        StateId marching = UnitAi.FORMATION_MARCHING;
        StateId holding = UnitAi.FORMATION_HOLDING;
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
                UnitAi.BURNING,
                UnitAi.GARRISON_APPROACHING,
                UnitAi.CAPTURE_APPROACHING,
                UnitAi.CAPTURING,
                UnitAi.ABILITY)) {
            out.add(workerRow(
                    "a formation order from " + from + " marches to its place",
                    from,
                    FormationTransitionRows::newFormation,
                    marching,
                    FormationTransitionRows::formationKept));
        }
        out.add(workerRow(
                "let out with a formation order waiting marches to its place",
                UnitAi.GARRISONED,
                FormationTransitionRows::releasedToFormation,
                marching,
                FormationTransitionRows::formationKept));
        out.add(workerRow(
                "a formation order while standing in one marches to its place",
                holding,
                FormationTransitionRows::newFormation,
                marching,
                FormationTransitionRows::formationKept));
        out.add(workerRow(
                "an arrival while the formation marches is nothing",
                marching,
                u -> new Arrived(u.handle),
                marching,
                FormationTransitionRows::formationKept));
        out.add(workerRow(
                "a formation that stands has its member on its place stand (a tick)",
                marching,
                FormationTransitionRows::standHere,
                holding,
                FormationTransitionRows::formationKept));
        out.add(workerRow(
                "pushed off its place a standing member walks back (a tick)",
                holding,
                FormationTransitionRows::pushed,
                marching,
                FormationTransitionRows::formationKept));
        out.add(workerRow(
                "an arrival while standing is nothing",
                holding,
                u -> new Arrived(u.handle),
                holding,
                FormationTransitionRows::formationKept));
        for (StateId from : List.of(marching, holding)) {
            addOwnOrders(out, from);
        }
        out.add(workerRow(
                "a formation that cannot be reached fails the order",
                marching,
                u -> new Stuck(u.handle, Stuck.GIVE_UP),
                UnitAi.IDLE,
                UnitTreeTransitionsTest.orderFinished(true)));
        out.add(workerRow(
                "a rung short of the last is nothing",
                marching,
                u -> new Stuck(u.handle, Stuck.GIVE_UP - 1),
                marching,
                FormationTransitionRows::formationKept));
        out.add(workerRow(
                "a blow while marching is not answered (it walks only)",
                marching,
                UnitTreeTransitionsTest::blow,
                marching,
                FormationTransitionRows::formationKept));
        out.add(workerRow(
                "a blow while standing in a formation that is still marching is not answered",
                holding,
                UnitTreeTransitionsTest::blow,
                holding,
                FormationTransitionRows::formationKept));
        return out.stream();
    }

    private static Arguments rowOf(
            boolean worker,
            String name,
            StateId from,
            Function<Unit, @Nullable BusMessage> message,
            StateId to,
            Consumer<Unit> effects) {
        return worker
                ? workerRow(name, from, message, to, effects)
                : UnitTreeTransitionsTest.row(name, from, message, to, effects);
    }

    /** What an order of its own does to a member in {@code from}: the state of its kind, in each. */
    static void addOwnOrders(List<Arguments> out, StateId from) {
        addOwnOrders(out, from, true);
    }

    /** As above, its rows run by a worker or, for {@code worker} false, by the unit tree's spearman. */
    static void addOwnOrders(List<Arguments> out, StateId from, boolean worker) {
        String in = " from " + from;
        out.add(rowOf(
                worker,
                "a stop" + in,
                from,
                UnitTreeTransitionsTest::stop,
                UnitAi.IDLE,
                UnitTreeTransitionsTest::nothingElse));
        out.add(rowOf(
                worker,
                "a move" + in,
                from,
                UnitTreeTransitionsTest::newMove,
                UnitAi.WALKING,
                UnitTreeTransitionsTest.kept(OrderKind.MOVE)));
        out.add(rowOf(
                worker,
                "a flight" + in,
                from,
                FleeTransitionRows::newFlee,
                UnitAi.FLEEING,
                UnitTreeTransitionsTest.kept(OrderKind.FLEE)));
        out.add(rowOf(
                worker,
                "an attack" + in,
                from,
                UnitTreeTransitionsTest::newAttack,
                UnitAi.APPROACHING,
                UnitTreeTransitionsTest.kept(OrderKind.ATTACK)));
        out.add(rowOf(
                worker,
                "an attack-move" + in,
                from,
                UnitTreeTransitionsTest::newAttackMove,
                UnitAi.WALKING_AND_FIGHTING,
                UnitTreeTransitionsTest.kept(OrderKind.ATTACK_MOVE)));
        out.add(rowOf(
                worker,
                "a follow" + in,
                from,
                UnitTreeTransitionsTest::newFollow,
                UnitAi.FOLLOW_WALKING,
                UnitTreeTransitionsTest.kept(OrderKind.FOLLOW)));
        out.add(rowOf(
                worker,
                "a gather order" + in,
                from,
                GatherTransitionRows::newGather,
                UnitAi.GATHER_APPROACHING,
                UnitTreeTransitionsTest.kept(OrderKind.GATHER)));
        out.add(rowOf(
                worker,
                "a build order" + in,
                from,
                BuildTransitionRows::newBuild,
                UnitAi.BUILD_APPROACHING,
                UnitTreeTransitionsTest.kept(OrderKind.BUILD)));
        out.add(rowOf(
                worker,
                "a burn order" + in,
                from,
                BurnTransitionRows::newBurn,
                UnitAi.BURN_APPROACHING,
                UnitTreeTransitionsTest.kept(OrderKind.BURN)));
        out.add(rowOf(
                worker,
                "a garrison order" + in,
                from,
                GarrisonTransitionRows::newGarrison,
                UnitAi.GARRISON_APPROACHING,
                UnitTreeTransitionsTest.kept(OrderKind.GARRISON)));
        out.add(rowOf(
                worker,
                "a capture order" + in,
                from,
                CaptureTransitionRows::newCapture,
                UnitAi.CAPTURE_APPROACHING,
                UnitTreeTransitionsTest.kept(OrderKind.CAPTURE)));
        out.add(rowOf(
                worker,
                "an ability order" + in,
                from,
                AbilityTransitionRows::newAbility,
                UnitAi.ABILITY,
                UnitTreeTransitionsTest.kept(OrderKind.ABILITY)));
    }
}
