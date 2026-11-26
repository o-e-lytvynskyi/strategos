package dev.strategos.core.sim.unitai;

import static dev.strategos.core.sim.unitai.UnitTreeTransitionsTest.row;
import static dev.strategos.core.sim.unitai.UnitTreeTransitionsTest.workerRow;
import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.Arrived;
import dev.strategos.core.sim.BusMessage;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.Stagger;
import dev.strategos.core.sim.Stuck;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.unitai.UnitTreeTransitionsTest.Unit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

/**
 * UNIT-02.1 for guarding (ORD-07): the GUARDING and GUARD_STANDING rows of the unit tree, run by {@link
 * UnitTreeTransitionsTest}. The charge is a friendly spearman 20 blocks east of the unit, at (24, 8); the enemy is
 * out of its sight until a row brings it next to the charge. (A patrol is carried out in WALKING_AND_FIGHTING and
 * declares no transition of its own.)
 */
final class GuardTransitionRows {
    private static final double CX = 24;
    private static final double CZ = 8;
    /** The guard states' think period. */
    private static final int THINK = 5;

    private GuardTransitionRows() {}

    /** The charge: a friendly spearman at (24, 8), made once, without a tick. */
    private static int charge(Unit u) {
        if (u.charge == 0) {
            int template = Objects.requireNonNull(u.state.templates().byId(TemplateSet.INFANTRY))
                    .netIndex();
            u.charge = u.state.spawnUnit(template, 0, CX, u.state.motion().y(u.index()), CZ, 0);
        }
        return u.charge;
    }

    /** The unit in {@code from}, as a save restores it: standing, it is 2 blocks from its charge. */
    static void start(Unit u, StateId from) {
        int charge = charge(u);
        double x = from.equals(UnitAi.GUARD_STANDING) ? CX - 2 : CX - Guarding.CLOSE; // walking: to its place
        if (from.equals(UnitAi.GUARD_STANDING)) {
            u.place(u.handle, x, CZ);
        }
        u.state.orders().replace(u.index(), OrderKind.GUARD, x, CZ, 0, 0, charge);
        u.state.ai().set(u.index(), from);
    }

    /** A guard order on the charge, 20 blocks from the unit, set as its rule sets it, then announced. */
    static BusMessage newGuard(Unit u) {
        int charge = charge(u);
        double x = u.state.motion().x(u.index());
        double z = u.state.motion().z(u.index());
        double cx = x < 16 ? x + 20 : x - 20;
        u.place(charge, cx, z);
        u.state.orders().replace(u.index(), OrderKind.GUARD, cx, z, 0, 0, charge);
        return new OrderReceived(u.handle);
    }

    /** A tick on which the unit thinks, after {@code change}: the ticks before it leave the row's start as it is. */
    private static BusMessage thinkAfter(Unit u, Runnable change) {
        while (!Stagger.selects(u.h.tick() + 1, u.index(), THINK)) {
            u.h.step();
        }
        change.run();
        return null;
    }

    /** The guard order waiting inside the house, announced as the release does: the unit is out of the store. */
    private static BusMessage releasedToGuard(Unit u) {
        BusMessage message = newGuard(u);
        u.state.garrison().remove(u.index());
        return message;
    }

    private static void guardKept(Unit u) {
        assertThat(u.state.orders().kind(u.index())).isEqualTo(OrderKind.GUARD);
        assertThat(u.state.orders().target(u.index())).isEqualTo(u.charge);
        assertThat(u.finished).isEmpty();
    }

    /** The enemy engaged before the guard order, which waits behind it. */
    private static void engagedFirst(Unit u) {
        assertThat(u.state.orders().kind(u.index())).isEqualTo(OrderKind.ENGAGE);
        assertThat(u.state.orders().target(u.index())).isEqualTo(u.enemy);
        assertThat(u.state.orders().size(u.index())).isEqualTo(2);
    }

    /** The unit is next to its charge: a tick. */
    private static BusMessage beside(Unit u) {
        return thinkAfter(u, () -> u.place(u.handle, CX - 2, CZ));
    }

    /** The charge walked on, 24 blocks from the unit and out of the enemy's reach: a tick. */
    private static BusMessage movedOn(Unit u) {
        return thinkAfter(u, () -> u.place(u.charge, 8, 28));
    }

    /** The enemy stands next to the charge, far from the unit: a tick. */
    private static BusMessage enemyNearCharge(Unit u) {
        return thinkAfter(u, () -> u.place(u.enemy, CX + 3, CZ));
    }

    private static BusMessage chargeDies(Unit u) {
        return thinkAfter(u, () -> u.state.removeUnit(u.charge));
    }

    /** The unit's order is a patrol between (4, 8) and (20, 8), on its first leg. */
    private static void patrolling(Unit u) {
        u.state
                .orders()
                .replace(u.index(), OrderKind.PATROL, 4, 8, 0, 0, 0, List.of(new Vec2d(4, 8), new Vec2d(20, 8)));
    }

    /** The patrol is on its second leg, to (20, 8), and its order stands. */
    private static void nextLeg(Unit u) {
        assertThat(u.state.orders().kind(u.index())).isEqualTo(OrderKind.PATROL);
        assertThat(u.state.orders().slot(u.index())).isEqualTo(1);
        assertThat(u.state.orders().targetX(u.index())).isEqualTo(20);
        assertThat(u.finished).isEmpty();
    }

    static Stream<Arguments> rows() {
        StateId walking = UnitAi.GUARDING;
        StateId standing = UnitAi.GUARD_STANDING;
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
                UnitAi.ABILITY,
                UnitAi.FORMATION_MARCHING,
                UnitAi.FORMATION_HOLDING)) {
            out.add(workerRow(
                    "a guard order from " + from + " walks to the charge",
                    from,
                    GuardTransitionRows::newGuard,
                    walking,
                    GuardTransitionRows::guardKept));
        }
        out.add(workerRow(
                "let out with a guard order waiting walks to the charge",
                UnitAi.GARRISONED,
                GuardTransitionRows::releasedToGuard,
                walking,
                GuardTransitionRows::guardKept));
        out.add(workerRow(
                "a guard order while standing beside the charge walks to it",
                standing,
                GuardTransitionRows::newGuard,
                walking,
                GuardTransitionRows::guardKept));
        out.add(row(
                "a charge that moved on is followed (a tick)",
                standing,
                GuardTransitionRows::movedOn,
                walking,
                GuardTransitionRows::guardKept));
        out.add(row(
                "arriving beside the charge stands",
                walking,
                u -> new Arrived(u.handle),
                standing,
                GuardTransitionRows::guardKept));
        out.add(row(
                "within reach of the charge stands (a tick)",
                walking,
                GuardTransitionRows::beside,
                standing,
                GuardTransitionRows::guardKept));
        out.add(row(
                "a charge that cannot be reached stands where the guard is",
                walking,
                u -> new Stuck(u.handle, Stuck.GIVE_UP),
                standing,
                GuardTransitionRows::guardKept));
        out.add(row(
                "a rung short of the last is nothing",
                walking,
                u -> new Stuck(u.handle, Stuck.GIVE_UP - 1),
                walking,
                GuardTransitionRows::guardKept));
        out.add(row(
                "an arrival while standing is nothing",
                standing,
                u -> new Arrived(u.handle),
                standing,
                GuardTransitionRows::guardKept));
        out.add(row(
                "a patrol's leg ending starts the next",
                UnitAi.WALKING_AND_FIGHTING,
                u -> {
                    patrolling(u);
                    return new Arrived(u.handle);
                },
                UnitAi.WALKING_AND_FIGHTING,
                GuardTransitionRows::nextLeg));
        out.add(row(
                "a patrol's leg no way leads to is skipped",
                UnitAi.WALKING_AND_FIGHTING,
                u -> {
                    patrolling(u);
                    return new Stuck(u.handle, Stuck.GIVE_UP);
                },
                UnitAi.WALKING_AND_FIGHTING,
                GuardTransitionRows::nextLeg));
        out.add(row(
                "a rung short of the last leaves the leg",
                UnitAi.WALKING_AND_FIGHTING,
                u -> {
                    patrolling(u);
                    return new Stuck(u.handle, Stuck.GIVE_UP - 1);
                },
                UnitAi.WALKING_AND_FIGHTING,
                u -> assertThat(u.state.orders().slot(u.index())).isZero()));
        for (StateId from : List.of(walking, standing)) {
            String in = " from " + from;
            out.add(row(
                    "an enemy next to the charge is engaged (a tick)" + in,
                    from,
                    GuardTransitionRows::enemyNearCharge,
                    UnitAi.APPROACHING,
                    GuardTransitionRows::engagedFirst));
            out.add(row(
                    "a blow is hit back, the guard order waiting" + in,
                    from,
                    UnitTreeTransitionsTest::blow,
                    UnitAi.APPROACHING,
                    GuardTransitionRows::engagedFirst));
            out.add(row(
                    "a charge that dies ends the order (a tick)" + in,
                    from,
                    GuardTransitionRows::chargeDies,
                    UnitAi.IDLE,
                    UnitTreeTransitionsTest.orderFinished(false)));
            FormationTransitionRows.addOwnOrders(out, from);
            out.add(row(
                    "a formation order" + in,
                    from,
                    FormationTransitionRows::newFormation,
                    UnitAi.FORMATION_MARCHING,
                    UnitTreeTransitionsTest.kept(OrderKind.FORMATION)));
        }
        return out.stream();
    }
}
