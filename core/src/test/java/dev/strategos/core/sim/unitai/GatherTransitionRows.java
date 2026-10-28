package dev.strategos.core.sim.unitai;

import static dev.strategos.core.sim.unitai.UnitTreeTransitionsTest.workerRow;
import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.Arrived;
import dev.strategos.core.sim.Attacked;
import dev.strategos.core.sim.BusMessage;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.Stuck;
import dev.strategos.core.sim.econ.Dropsites;
import dev.strategos.core.sim.econ.NodeStore;
import dev.strategos.core.sim.nav.MaterialKind;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.unitai.UnitTreeTransitionsTest.Unit;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

/**
 * UNIT-02.1 for gathering (ECO-05): the GATHER states' rows of the unit tree, run by {@link UnitTreeTransitionsTest}.
 * The unit is a worker at (4, 8) beside an oak of five logs at (12, 8), the faction's civic centre in the north-east
 * and a house's foundation in the south-east ({@link BuildTransitionRows}).
 */
final class GatherTransitionRows {
    static final TemplateId CIVIC_CENTRE = new TemplateId("strategos:building/civic_centre");
    static final int WOOD = 1;
    static final long LOG = 20_000;

    private GatherTransitionRows() {}

    /** The tree and the dropsite, the tree's wood found as discovery would record it. */
    static void setUp(Unit u) {
        MatchState s = u.state;
        long[] logs = new long[5];
        for (int k = 0; k < logs.length; k++) {
            logs[k] = NodeStore.pack(12, 5 - k, 8); // top-down
        }
        int[] ring = new int[8];
        int n = 0;
        for (int x = 11; x <= 13; x++) {
            for (int z = 7; z <= 9; z++) {
                if (x != 12 || z != 8) {
                    ring[n++] = NodeStore.cell(x, z);
                }
            }
        }
        u.node = s.nodes().add(MaterialKind.LOG, WOOD, 0, LOG, 5 * LOG, 12.5, 8.5, 1, logs, ring);
        s.ledger().found(WOOD, 5 * LOG);
        int template = Objects.requireNonNull(s.templates().byId(CIVIC_CENTRE)).netIndex();
        Blueprint b = Objects.requireNonNull(s.templates().blueprint(template));
        u.dropsite = s.placeComplete(template, 0, Footprint.of(b, 20, 0, 0), 1);
        BuildTransitionRows.setUp(u);
    }

    /** The unit in {@code from}, as a save restores it: its gather order, its slot, and for the way home its load. */
    static void start(Unit u, StateId from) {
        MatchState s = u.state;
        int i = u.index();
        int slot = s.nodes().claimNearest(u.node, u.handle, 4, 8);
        s.carry().setClaimed(i, u.node);
        int cell = s.nodes().slot(u.node, slot);
        s.orders().replace(i, OrderKind.GATHER, (cell >>> 16) + 0.5, (cell & 0xFFFF) + 0.5, 0, slot, u.node);
        s.ai().set(i, from);
        if (from.equals(UnitAi.GATHERING)) {
            u.place(u.handle, (cell >>> 16) + 0.5, (cell & 0xFFFF) + 0.5);
        }
        if (from.equals(UnitAi.GATHER_RETURNING)) {
            carry(u, 10_000);
            s.ai().setData(i, u.dropsite, 0);
            double[] at = Dropsites.dropPoint(s.footprintOf(u.dropsite), 4, 8);
            s.orders().setPlace(i, at[0], at[1]);
        }
    }

    /** The unit carries {@code amount} thousandths of wood, taken from its tree. */
    static void carry(Unit u, long amount) {
        u.state.nodes().take(u.node, amount);
        u.state.carry().add(u.index(), WOOD, 0, amount);
        u.state.ledger().taken(WOOD, amount);
    }

    /** A gather order on the oak, set as the gather rule sets it, then announced. */
    static BusMessage newGather(Unit u) {
        u.state.orders().replace(u.index(), OrderKind.GATHER, 12.5, 8.5, 0, 0, u.node);
        return new OrderReceived(u.handle);
    }

    /** The player's attack on the enemy, standing 6 blocks south of the tree, out of a worker's reach at its slot. */
    static BusMessage newAttackAway(Unit u) {
        u.place(u.enemy, 12, 16);
        u.state.orders().replace(u.index(), OrderKind.ATTACK, 12, 16, 0, 0, u.enemy);
        return new OrderReceived(u.handle);
    }

    /** A worker's blow from the enemy, which it runs from. */
    static BusMessage blow(Unit u) {
        return new Attacked(u.handle, u.enemy);
    }

    private static Consumer<Unit> gathering(int expectedSlotHolder) {
        return u -> {
            assertThat(u.state.orders().kind(u.index())).isEqualTo(OrderKind.GATHER);
            assertThat(u.state.orders().target(u.index())).isEqualTo(u.node);
            assertThat(u.state.carry().claimed(u.index())).isEqualTo(expectedSlotHolder == 0 ? 0 : u.node);
        };
    }

    /** Its slot given up: leaving GATHER frees it. */
    private static Consumer<Unit> slotFreed(Consumer<Unit> then) {
        return u -> {
            assertThat(u.state.carry().claimed(u.index())).isZero();
            assertThat(u.state.nodes().freeSlots(u.node)).isEqualTo(8);
            then.accept(u);
        };
    }

    static Stream<Arguments> rows() {
        StateId idle = UnitAi.IDLE;
        StateId walking = UnitAi.WALKING;
        StateId fighting = UnitAi.WALKING_AND_FIGHTING;
        StateId approaching = UnitAi.APPROACHING;
        StateId attacking = UnitAi.ATTACKING;
        StateId chasing = UnitAi.CHASING;
        StateId following = UnitAi.FOLLOW_WALKING;
        StateId waiting = UnitAi.FOLLOW_WAITING;
        StateId toNode = UnitAi.GATHER_APPROACHING;
        StateId working = UnitAi.GATHERING;
        StateId home = UnitAi.GATHER_RETURNING;
        return Stream.of(
                // from every other leaf, a gather order walks to a slot
                workerRow(
                        "a gather order walks to a slot", idle, GatherTransitionRows::newGather, toNode, gathering(1)),
                workerRow(
                        "a gather order while walking", walking, GatherTransitionRows::newGather, toNode, gathering(1)),
                workerRow(
                        "a gather order while walking and fighting",
                        fighting,
                        GatherTransitionRows::newGather,
                        toNode,
                        gathering(1)),
                workerRow(
                        "a gather order while closing in",
                        approaching,
                        GatherTransitionRows::newGather,
                        toNode,
                        gathering(1)),
                workerRow(
                        "a gather order while striking",
                        attacking,
                        GatherTransitionRows::newGather,
                        toNode,
                        gathering(1)),
                workerRow(
                        "a gather order while chasing", chasing, GatherTransitionRows::newGather, toNode, gathering(1)),
                workerRow(
                        "a gather order while following",
                        following,
                        GatherTransitionRows::newGather,
                        toNode,
                        gathering(1)),
                workerRow(
                        "a gather order while waiting behind the leader",
                        waiting,
                        GatherTransitionRows::newGather,
                        toNode,
                        gathering(1)),
                // walking to the slot
                workerRow(
                        "arriving at the slot works",
                        toNode,
                        u -> {
                            u.place(
                                    u.handle,
                                    u.state.orders().targetX(u.index()),
                                    u.state.orders().targetZ(u.index()));
                            return new Arrived(u.handle);
                        },
                        working,
                        gathering(1)),
                workerRow(
                        "arriving short of the slot walks on",
                        toNode,
                        u -> new Arrived(u.handle),
                        toNode,
                        gathering(1)),
                workerRow(
                        "a new gather order with a full load goes home first",
                        toNode,
                        u -> {
                            carry(u, 10_000);
                            return newGather(u);
                        },
                        home,
                        u -> assertThat(u.state.ai().data0(u.index())).isEqualTo(u.dropsite)),
                workerRow(
                        "a stop on the way to the slot idles",
                        toNode,
                        UnitTreeTransitionsTest::stop,
                        idle,
                        slotFreed(UnitTreeTransitionsTest::nothingElse)),
                workerRow(
                        "a move on the way to the slot",
                        toNode,
                        UnitTreeTransitionsTest::newMove,
                        walking,
                        slotFreed(UnitTreeTransitionsTest.kept(OrderKind.MOVE))),
                workerRow(
                        "an attack on the way to the slot",
                        toNode,
                        UnitTreeTransitionsTest::newAttack,
                        approaching,
                        slotFreed(UnitTreeTransitionsTest.kept(OrderKind.ATTACK))),
                workerRow(
                        "an attack-move on the way to the slot",
                        toNode,
                        UnitTreeTransitionsTest::newAttackMove,
                        fighting,
                        slotFreed(UnitTreeTransitionsTest.kept(OrderKind.ATTACK_MOVE))),
                workerRow(
                        "a follow on the way to the slot",
                        toNode,
                        UnitTreeTransitionsTest::newFollow,
                        following,
                        slotFreed(UnitTreeTransitionsTest.kept(OrderKind.FOLLOW))),
                workerRow(
                        "a slot the worker cannot reach fails the order",
                        toNode,
                        u -> new Stuck(u.handle, Stuck.GIVE_UP),
                        idle,
                        slotFreed(UnitTreeTransitionsTest.orderFinished(true))),
                // working
                workerRow(
                        "a full load goes home (a tick)",
                        working,
                        u -> {
                            carry(u, 10_000);
                            return null;
                        },
                        home,
                        u -> assertThat(u.state.ai().data0(u.index())).isEqualTo(u.dropsite)),
                workerRow(
                        "a new gather order while working walks to a slot again",
                        working,
                        GatherTransitionRows::newGather,
                        toNode,
                        gathering(1)),
                workerRow(
                        "a move while working",
                        working,
                        UnitTreeTransitionsTest::newMove,
                        walking,
                        slotFreed(UnitTreeTransitionsTest.kept(OrderKind.MOVE))),
                workerRow(
                        "a worker hit while working runs, its gathering waiting",
                        working,
                        GatherTransitionRows::blow,
                        UnitAi.FLEEING,
                        slotFreed(u -> {
                            assertThat(u.state.orders().kind(u.index())).isEqualTo(OrderKind.FLEE);
                            assertThat(u.state.orders().order(u.index(), 1).kind())
                                    .isEqualTo(OrderKind.GATHER);
                        })),
                workerRow(
                        "a stop while working idles",
                        working,
                        UnitTreeTransitionsTest::stop,
                        idle,
                        slotFreed(UnitTreeTransitionsTest::nothingElse)),
                workerRow(
                        "an attack while working",
                        working,
                        GatherTransitionRows::newAttackAway,
                        approaching,
                        slotFreed(UnitTreeTransitionsTest.kept(OrderKind.ATTACK))),
                workerRow(
                        "an attack-move while working",
                        working,
                        UnitTreeTransitionsTest::newAttackMove,
                        fighting,
                        slotFreed(UnitTreeTransitionsTest.kept(OrderKind.ATTACK_MOVE))),
                workerRow(
                        "a follow while working",
                        working,
                        UnitTreeTransitionsTest::newFollow,
                        following,
                        slotFreed(UnitTreeTransitionsTest.kept(OrderKind.FOLLOW))),
                // the way home
                workerRow(
                        "arriving at the dropsite delivers and goes back",
                        home,
                        u -> {
                            u.place(
                                    u.handle,
                                    u.state.orders().targetX(u.index()),
                                    u.state.orders().targetZ(u.index()));
                            return new Arrived(u.handle);
                        },
                        toNode,
                        u -> {
                            assertThat(u.state.carry().amount(u.index())).isZero();
                            assertThat(u.state.treasury().gathered(0, WOOD)).isEqualTo(10_000);
                            gathering(1).accept(u);
                        }),
                workerRow(
                        "arriving short of the dropsite walks on",
                        home,
                        u -> new Arrived(u.handle),
                        home,
                        u -> assertThat(u.state.carry().amount(u.index())).isEqualTo(10_000)),
                workerRow(
                        "a stop on the way home idles, keeping the load",
                        home,
                        UnitTreeTransitionsTest::stop,
                        idle,
                        slotFreed(u ->
                                assertThat(u.state.carry().amount(u.index())).isEqualTo(10_000))),
                workerRow(
                        "a move on the way home",
                        home,
                        UnitTreeTransitionsTest::newMove,
                        walking,
                        slotFreed(UnitTreeTransitionsTest.kept(OrderKind.MOVE))),
                workerRow(
                        "an attack on the way home",
                        home,
                        UnitTreeTransitionsTest::newAttack,
                        approaching,
                        slotFreed(UnitTreeTransitionsTest.kept(OrderKind.ATTACK))),
                workerRow(
                        "an attack-move on the way home",
                        home,
                        UnitTreeTransitionsTest::newAttackMove,
                        fighting,
                        slotFreed(UnitTreeTransitionsTest.kept(OrderKind.ATTACK_MOVE))),
                workerRow(
                        "a follow on the way home",
                        home,
                        UnitTreeTransitionsTest::newFollow,
                        following,
                        slotFreed(UnitTreeTransitionsTest.kept(OrderKind.FOLLOW))));
    }
}
