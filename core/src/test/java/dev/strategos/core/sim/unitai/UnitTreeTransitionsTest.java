package dev.strategos.core.sim.unitai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.Arrived;
import dev.strategos.core.sim.Attacked;
import dev.strategos.core.sim.BusMessage;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.OrderFinished;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.PathDelivered;
import dev.strategos.core.sim.PathInvalidated;
import dev.strategos.core.sim.StatsChanged;
import dev.strategos.core.sim.StopOrdered;
import dev.strategos.core.sim.Stuck;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitStateChanged;
import dev.strategos.core.sim.nav.PathStatus;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.stats.StatScope;
import dev.strategos.core.testkit.SimHarness;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * UNIT-02.1: the unit tree, one row per message and state, each through the match's own bus and routing (and, for a
 * change only a think makes, a tick). The framework's own rules are {@link HfsmTransitionsTest}'s.
 */
class UnitTreeTransitionsTest {
    /**
     * One spearman of a real match and an enemy spearman, in the row's start state: IDLE; WALKING by an order and a
     * tick; any other set as data, as a save restores it, with the enemy placed for it. A gather row's unit is a worker
     * instead, beside a tree ({@link GatherTransitionRows#setUp}), with a torch and an enemy house ({@link
     * BurnTransitionRows#setUp}). A row publishes on the bus between ticks, as a system
     * would, or steps a tick.
     */
    static final class Unit {
        final SimHarness h;
        final int handle;
        final int enemy;
        final MatchState state;
        final List<OrderFinished> finished = new ArrayList<>();
        int node;
        int dropsite;
        int site;
        int enemyHouse;

        Unit(StateId from, boolean worker) {
            h = worker
                    ? SimHarness.builder()
                            .templates(BurnTransitionRows.torchWorkers())
                            .build()
                    : SimHarness.builder().build();
            state = KernelAccess.state(h.core());
            handle = spawn(h, worker ? TemplateSet.WORKER : TemplateSet.INFANTRY, 0, new Vec2d(4, 8));
            enemy = spawn(h, TemplateSet.INFANTRY, 1, new Vec2d(28, 28)); // 31 blocks off: out of sight
            state.bus().subscribe(OrderFinished.class, finished::add);
            int i = index();
            if (worker) {
                GatherTransitionRows.setUp(this);
                BurnTransitionRows.setUp(this);
            }
            if (from.equals(UnitAi.WALKING)) {
                h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(handle), new Vec2d(20, 8)));
                h.step();
            } else if (from.equals(UnitAi.WALKING_AND_FIGHTING)) {
                state.orders().replace(i, OrderKind.ATTACK_MOVE, 20, 8, 0, 0);
                state.ai().set(i, from);
            } else if (from.equals(UnitAi.FOLLOW_WALKING) || from.equals(UnitAi.FOLLOW_WAITING)) {
                state.orders().replace(i, OrderKind.FOLLOW, 4, 8, 0, 0, 0); // behind slot 0's commander
                state.ai().set(i, from);
            } else if (from.equals(UnitAi.GATHER_APPROACHING)
                    || from.equals(UnitAi.GATHERING)
                    || from.equals(UnitAi.GATHER_RETURNING)) {
                GatherTransitionRows.start(this, from);
            } else if (from.equals(UnitAi.BUILD_APPROACHING) || from.equals(UnitAi.BUILDING)) {
                BuildTransitionRows.start(this, from);
            } else if (from.equals(UnitAi.BURN_APPROACHING) || from.equals(UnitAi.BURNING)) {
                BurnTransitionRows.start(this, from);
            } else if (from.equals(UnitAi.FLEEING)) {
                FleeTransitionRows.start(this);
            } else if (from.equals(UnitAi.GARRISON_APPROACHING) || from.equals(UnitAi.GARRISONED)) {
                GarrisonTransitionRows.start(this, from);
            } else if (!from.equals(UnitAi.IDLE)) {
                double ex = from.equals(UnitAi.ATTACKING) ? 5.2 : 10; // in reach (a 0.5 gap), or 6 blocks off
                place(enemy, ex, 8);
                state.orders().replace(i, OrderKind.ATTACK, ex, 8, 0, 0, enemy);
                state.ai().set(i, from);
                state.ai().setData(i, from.equals(UnitAi.ATTACKING) ? 10 : 0, 0);
            }
            assertThat(leaf()).as("the row's start").isEqualTo(from);
        }

        int index() {
            return Handle.index(handle);
        }

        StateId leaf() {
            return UnitAi.leaf(state.ai().code(index()));
        }

        void place(int unit, double x, double z) {
            int k = Handle.index(unit);
            state.motion().set(k, x, state.motion().y(k), z, 0);
        }

        /** Publishes {@code message}, or steps a tick for none; the unit's trace events that added. */
        List<UnitStateChanged> publish(@Nullable BusMessage message) {
            List<OutputEvent> added;
            if (message == null) {
                added = h.step().events();
            } else {
                int before = state.outputs().size();
                state.bus().publish(message);
                added = state.outputs().subList(before, state.outputs().size());
            }
            List<UnitStateChanged> events = new ArrayList<>();
            for (OutputEvent e : added) {
                if (e instanceof UnitStateChanged change && change.handle() == handle) {
                    events.add(change);
                }
            }
            return events;
        }
    }

    private static int spawn(SimHarness h, TemplateId template, int faction, Vec2d at) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(template, new FactionSlot(faction), at, 1, 0));
        return h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .findFirst()
                .orElseThrow();
    }

    private static Arguments row(
            String name,
            StateId from,
            Function<Unit, @Nullable BusMessage> message,
            StateId to,
            Consumer<Unit> effects) {
        return Arguments.of(name, from, message, to, effects, false);
    }

    /** A row whose unit is a worker beside a tree, a dropsite in the east ({@link GatherTransitionRows}). */
    static Arguments workerRow(
            String name,
            StateId from,
            Function<Unit, @Nullable BusMessage> message,
            StateId to,
            Consumer<Unit> effects) {
        return Arguments.of(name, from, message, to, effects, true);
    }

    /** A move order to (20, 20) set as the move rule sets it, then announced. */
    static BusMessage newMove(Unit u) {
        u.state.orders().replaceMove(u.index(), 20, 20);
        return new OrderReceived(u.handle);
    }

    /** The player's attack on the enemy, 6 blocks off, set as the attack rule sets it, then announced. */
    static BusMessage newAttack(Unit u) {
        u.place(u.enemy, 10, 8);
        u.state.orders().replace(u.index(), OrderKind.ATTACK, 10, 8, 0, 0, u.enemy);
        return new OrderReceived(u.handle);
    }

    /** An attack-move to (20, 20), set as its rule sets it, then announced. */
    static BusMessage newAttackMove(Unit u) {
        u.state.orders().replace(u.index(), OrderKind.ATTACK_MOVE, 20, 20, 0, 0);
        return new OrderReceived(u.handle);
    }

    /** Following slot 0's commander, set as the follow rule sets it, then announced. */
    static BusMessage newFollow(Unit u) {
        u.state.orders().replace(u.index(), OrderKind.FOLLOW, 4, 8, 0, 0, 0);
        return new OrderReceived(u.handle);
    }

    /** A stop as the stop rule makes it: the order cleared, then announced. */
    static BusMessage stop(Unit u) {
        u.state.orders().clear(u.index());
        return new StopOrdered(u.handle);
    }

    /** The enemy's blow, as the COMBAT phase tells of one the unit lived through. */
    static BusMessage blow(Unit u) {
        return new Attacked(u.handle, u.enemy);
    }

    /** The enemy stands in reach and the unit arrives. */
    private static BusMessage arrivedInReach(Unit u) {
        u.place(u.enemy, 5.2, 8);
        return new Arrived(u.handle);
    }

    static void nothingElse(Unit u) {
        assertThat(u.finished).isEmpty();
        assertThat(u.state.paths().repath(u.index())).isFalse();
    }

    private static void orderKept(Unit u) {
        assertThat(u.state.orders().kind(u.index())).isEqualTo(OrderKind.MOVE);
        nothingElse(u);
    }

    static Consumer<Unit> kept(int kind) {
        return u -> {
            assertThat(u.state.orders().kind(u.index())).isEqualTo(kind);
            assertThat(u.finished).isEmpty();
        };
    }

    private static Consumer<Unit> engaged(int before) {
        return u -> {
            assertThat(u.state.orders().kind(u.index())).isEqualTo(OrderKind.ENGAGE);
            assertThat(u.state.orders().target(u.index())).isEqualTo(u.enemy);
            assertThat(u.state.orders().size(u.index()))
                    .as("its orders wait behind it")
                    .isEqualTo(before + 1);
        };
    }

    static Consumer<Unit> orderFinished(boolean failed) {
        return u -> {
            assertThat(u.state.orders().kind(u.index())).isEqualTo(OrderKind.NONE);
            assertThat(u.finished).containsExactly(new OrderFinished(u.handle, failed));
        };
    }

    static Stream<Arguments> unitRows() {
        return Stream.of(
                        rows(),
                        GatherTransitionRows.rows(),
                        BuildTransitionRows.rows(),
                        BurnTransitionRows.rows(),
                        FleeTransitionRows.rows(),
                        GarrisonTransitionRows.rows())
                .flatMap(r -> r);
    }

    private static Stream<Arguments> rows() {
        StateId idle = UnitAi.IDLE;
        StateId walking = UnitAi.WALKING;
        StateId fighting = UnitAi.WALKING_AND_FIGHTING;
        StateId approaching = UnitAi.APPROACHING;
        StateId attacking = UnitAi.ATTACKING;
        StateId chasing = UnitAi.CHASING;
        StateId following = UnitAi.FOLLOW_WALKING;
        StateId waiting = UnitAi.FOLLOW_WAITING;
        return Stream.of(
                row(
                        "an order starts walking (the root's handler)",
                        idle,
                        UnitTreeTransitionsTest::newMove,
                        walking,
                        u -> {
                            assertThat(u.state.orders().targetZ(u.index())).isEqualTo(20);
                            nothingElse(u);
                        }),
                row(
                        "a new order while walking carries on toward its target",
                        walking,
                        UnitTreeTransitionsTest::newMove,
                        walking,
                        u -> {
                            assertThat(u.state.orders().targetZ(u.index())).isEqualTo(20);
                            nothingElse(u);
                        }),
                row(
                        "a stop while walking idles (the root's handler, inherited)",
                        walking,
                        UnitTreeTransitionsTest::stop,
                        idle,
                        UnitTreeTransitionsTest::nothingElse),
                row(
                        "a stop while idle does nothing (IDLE's own handler)",
                        idle,
                        UnitTreeTransitionsTest::stop,
                        idle,
                        UnitTreeTransitionsTest::nothingElse),
                row("an arrival finishes the order", walking, u -> new Arrived(u.handle), idle, orderFinished(false)),
                row(
                        "the stuck ladder's last rung fails the order",
                        walking,
                        u -> new Stuck(u.handle, Stuck.GIVE_UP),
                        idle,
                        orderFinished(true)),
                row(
                        "a lower stuck rung leaves the order to the ladder",
                        walking,
                        u -> new Stuck(u.handle, Stuck.GIVE_UP - 1),
                        walking,
                        UnitTreeTransitionsTest::orderKept),
                row(
                        "an invalidated path marks the unit to ask again",
                        walking,
                        u -> new PathInvalidated(u.handle),
                        walking,
                        u -> assertThat(u.state.paths().repath(u.index())).isTrue()),
                row(
                        "a delivered path changes no state",
                        walking,
                        u -> new PathDelivered(u.handle, PathStatus.PARTIAL),
                        walking,
                        UnitTreeTransitionsTest::orderKept),
                row(
                        "a finished order is no message for the states",
                        walking,
                        u -> new OrderFinished(u.handle, false),
                        walking,
                        u -> assertThat(u.state.orders().kind(u.index())).isEqualTo(OrderKind.MOVE)),
                row(
                        "changed stats reach no machine",
                        walking,
                        u -> new StatsChanged(StatScope.UNIT, u.handle, List.of("UnitMotion/walkSpeed")),
                        walking,
                        UnitTreeTransitionsTest::orderKept),
                row(
                        "a blow while walking is no reason to stop (a player's move)",
                        walking,
                        UnitTreeTransitionsTest::blow,
                        walking,
                        UnitTreeTransitionsTest::orderKept),
                row(
                        "an arrival while idle is dropped at the root",
                        idle,
                        u -> new Arrived(u.handle),
                        idle,
                        UnitTreeTransitionsTest::nothingElse),
                row(
                        "the last stuck rung while idle is dropped",
                        idle,
                        u -> new Stuck(u.handle, Stuck.GIVE_UP),
                        idle,
                        UnitTreeTransitionsTest::nothingElse),
                row(
                        "an invalidated path while idle is dropped",
                        idle,
                        u -> new PathInvalidated(u.handle),
                        idle,
                        UnitTreeTransitionsTest::nothingElse),
                // CMB-02 and ORD-04
                row(
                        "an attack order closes in",
                        idle,
                        UnitTreeTransitionsTest::newAttack,
                        approaching,
                        kept(OrderKind.ATTACK)),
                row(
                        "an attack while walking closes in",
                        walking,
                        UnitTreeTransitionsTest::newAttack,
                        approaching,
                        kept(OrderKind.ATTACK)),
                row(
                        "an attack-move walks and fights",
                        idle,
                        UnitTreeTransitionsTest::newAttackMove,
                        fighting,
                        kept(OrderKind.ATTACK_MOVE)),
                row(
                        "an attack-move while walking",
                        walking,
                        UnitTreeTransitionsTest::newAttackMove,
                        fighting,
                        kept(OrderKind.ATTACK_MOVE)),
                row("an idle unit hit hits back", idle, UnitTreeTransitionsTest::blow, approaching, engaged(0)),
                row(
                        "an attack-move's arrival finishes it",
                        fighting,
                        u -> new Arrived(u.handle),
                        idle,
                        orderFinished(false)),
                row(
                        "a move while walking and fighting",
                        fighting,
                        UnitTreeTransitionsTest::newMove,
                        walking,
                        kept(OrderKind.MOVE)),
                row(
                        "a unit walking and fighting hits back, its walk waiting",
                        fighting,
                        UnitTreeTransitionsTest::blow,
                        approaching,
                        engaged(1)),
                row(
                        "an attack while walking and fighting",
                        fighting,
                        UnitTreeTransitionsTest::newAttack,
                        approaching,
                        kept(OrderKind.ATTACK)),
                row(
                        "a stop while walking and fighting idles",
                        fighting,
                        UnitTreeTransitionsTest::stop,
                        idle,
                        UnitTreeTransitionsTest::nothingElse),
                row(
                        "a stop while closing in idles",
                        approaching,
                        UnitTreeTransitionsTest::stop,
                        idle,
                        UnitTreeTransitionsTest::nothingElse),
                row(
                        "a move while closing in",
                        approaching,
                        UnitTreeTransitionsTest::newMove,
                        walking,
                        kept(OrderKind.MOVE)),
                row(
                        "an attack-move while closing in",
                        approaching,
                        UnitTreeTransitionsTest::newAttackMove,
                        fighting,
                        kept(OrderKind.ATTACK_MOVE)),
                row(
                        "arriving in reach strikes",
                        approaching,
                        UnitTreeTransitionsTest::arrivedInReach,
                        attacking,
                        kept(OrderKind.ATTACK)),
                row(
                        "arriving short of reach takes a free place and walks on",
                        approaching,
                        u -> new Arrived(u.handle),
                        approaching,
                        kept(OrderKind.ATTACK)),
                row(
                        "an attack the unit cannot reach fails",
                        approaching,
                        u -> new Stuck(u.handle, Stuck.GIVE_UP),
                        idle,
                        orderFinished(true)),
                row(
                        "a blow while closing in keeps the target",
                        approaching,
                        UnitTreeTransitionsTest::blow,
                        approaching,
                        kept(OrderKind.ATTACK)),
                row(
                        "a stop while striking idles",
                        attacking,
                        UnitTreeTransitionsTest::stop,
                        idle,
                        UnitTreeTransitionsTest::nothingElse),
                row(
                        "a move while striking",
                        attacking,
                        UnitTreeTransitionsTest::newMove,
                        walking,
                        kept(OrderKind.MOVE)),
                row(
                        "an attack-move while striking",
                        attacking,
                        UnitTreeTransitionsTest::newAttackMove,
                        fighting,
                        kept(OrderKind.ATTACK_MOVE)),
                row(
                        "a new attack while striking closes in on it afresh",
                        attacking,
                        UnitTreeTransitionsTest::newAttack,
                        approaching,
                        kept(OrderKind.ATTACK)),
                row(
                        "a target stepping out of reach is chased (a tick)",
                        attacking,
                        u -> {
                            u.place(u.enemy, 9, 8);
                            return null;
                        },
                        chasing,
                        kept(OrderKind.ATTACK)),
                row(
                        "a stop while chasing idles",
                        chasing,
                        UnitTreeTransitionsTest::stop,
                        idle,
                        UnitTreeTransitionsTest::nothingElse),
                row("a move while chasing", chasing, UnitTreeTransitionsTest::newMove, walking, kept(OrderKind.MOVE)),
                row(
                        "an attack-move while chasing",
                        chasing,
                        UnitTreeTransitionsTest::newAttackMove,
                        fighting,
                        kept(OrderKind.ATTACK_MOVE)),
                row(
                        "a new attack while chasing closes in afresh",
                        chasing,
                        UnitTreeTransitionsTest::newAttack,
                        approaching,
                        kept(OrderKind.ATTACK)),
                row(
                        "catching up strikes",
                        chasing,
                        UnitTreeTransitionsTest::arrivedInReach,
                        attacking,
                        kept(OrderKind.ATTACK)),
                // CAM-07
                row(
                        "a follow order walks behind",
                        idle,
                        UnitTreeTransitionsTest::newFollow,
                        following,
                        kept(OrderKind.FOLLOW)),
                row(
                        "a follow while walking",
                        walking,
                        UnitTreeTransitionsTest::newFollow,
                        following,
                        kept(OrderKind.FOLLOW)),
                row(
                        "a follow while walking and fighting",
                        fighting,
                        UnitTreeTransitionsTest::newFollow,
                        following,
                        kept(OrderKind.FOLLOW)),
                row(
                        "a follow while closing in",
                        approaching,
                        UnitTreeTransitionsTest::newFollow,
                        following,
                        kept(OrderKind.FOLLOW)),
                row(
                        "a follow while striking",
                        attacking,
                        UnitTreeTransitionsTest::newFollow,
                        following,
                        kept(OrderKind.FOLLOW)),
                row(
                        "a follow while chasing",
                        chasing,
                        UnitTreeTransitionsTest::newFollow,
                        following,
                        kept(OrderKind.FOLLOW)),
                row(
                        "a follower at its place waits",
                        following,
                        u -> new Arrived(u.handle),
                        waiting,
                        kept(OrderKind.FOLLOW)),
                row(
                        "a stop while following idles",
                        following,
                        UnitTreeTransitionsTest::stop,
                        idle,
                        UnitTreeTransitionsTest::nothingElse),
                row(
                        "a move while following",
                        following,
                        UnitTreeTransitionsTest::newMove,
                        walking,
                        kept(OrderKind.MOVE)),
                row(
                        "an attack while following",
                        following,
                        UnitTreeTransitionsTest::newAttack,
                        approaching,
                        kept(OrderKind.ATTACK)),
                row(
                        "an attack-move while following",
                        following,
                        UnitTreeTransitionsTest::newAttackMove,
                        fighting,
                        kept(OrderKind.ATTACK_MOVE)),
                row(
                        "a follower that cannot get there waits",
                        following,
                        u -> new Stuck(u.handle, Stuck.GIVE_UP),
                        waiting,
                        kept(OrderKind.FOLLOW)),
                row(
                        "a new follow while waiting walks afresh",
                        waiting,
                        UnitTreeTransitionsTest::newFollow,
                        following,
                        kept(OrderKind.FOLLOW)),
                row(
                        "a stop while waiting idles",
                        waiting,
                        UnitTreeTransitionsTest::stop,
                        idle,
                        UnitTreeTransitionsTest::nothingElse),
                row("a move while waiting", waiting, UnitTreeTransitionsTest::newMove, walking, kept(OrderKind.MOVE)),
                row(
                        "an attack while waiting",
                        waiting,
                        UnitTreeTransitionsTest::newAttack,
                        approaching,
                        kept(OrderKind.ATTACK)),
                row(
                        "an attack-move while waiting",
                        waiting,
                        UnitTreeTransitionsTest::newAttackMove,
                        fighting,
                        kept(OrderKind.ATTACK_MOVE)),
                row(
                        "a blow while waiting is not hit back (it follows)",
                        waiting,
                        UnitTreeTransitionsTest::blow,
                        waiting,
                        kept(OrderKind.FOLLOW)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unitRows")
    void theUnitTree(
            String name,
            StateId from,
            Function<Unit, @Nullable BusMessage> message,
            StateId to,
            Consumer<Unit> effects,
            boolean worker) {
        Unit u = new Unit(from, worker);

        List<UnitStateChanged> events = u.publish(message.apply(u));

        assertThat(u.leaf()).isEqualTo(to);
        UnitState was = from.activity();
        UnitState now = to.activity();
        if (was == now) {
            assertThat(events)
                    .as("no trace event without a change of top-level state")
                    .isEmpty();
        } else {
            assertThat(events).containsExactly(new UnitStateChanged(u.handle, was, now));
        }
        effects.accept(u);
    }

    @Test
    void everyTransitionTheTreeDeclaresHasARow() {
        Hfsm<MatchState> tree = UnitAi.tree();
        List<String> declared = new ArrayList<>();
        for (StateId leaf : tree.leaves()) {
            for (StateId to : HfsmCheck.next(tree, leaf)) {
                if (!to.equals(leaf)) { // re-entering itself is no transition a row can tell from staying
                    declared.add(leaf + " -> " + to);
                }
            }
        }
        List<String> rows = unitRows()
                .map(Arguments::get)
                .filter(a -> !a[1].equals(a[3]))
                .map(a -> a[1] + " -> " + a[3])
                .distinct()
                .toList();
        assertThat(rows).containsExactlyInAnyOrderElementsOf(declared);
    }

    @Test
    void theStatesThinkAtTheirPeriods() {
        Hfsm<MatchState> tree = UnitAi.tree();
        assertThat(tree.thinkPeriod(UnitAi.IDLE)).isEqualTo(10);
        assertThat(tree.thinkPeriod(UnitAi.WALKING)).isEqualTo(5);
        assertThat(tree.thinkPeriod(UnitAi.WALKING_AND_FIGHTING)).isEqualTo(5);
        assertThat(tree.thinkPeriod(UnitAi.APPROACHING)).isEqualTo(5);
        assertThat(tree.thinkPeriod(UnitAi.ATTACKING)).isEqualTo(1);
        assertThat(tree.thinkPeriod(UnitAi.CHASING)).isEqualTo(5);
        assertThat(tree.thinkPeriod(UnitAi.FOLLOW_WALKING)).isEqualTo(5);
        assertThat(tree.thinkPeriod(UnitAi.FOLLOW_WAITING)).isEqualTo(5);
        MatchState state = KernelAccess.state(SimHarness.builder().build().core());
        assertThat(tree.machine(state, state.ai()).thinks()).isTrue();
    }

    @Test
    void aTreeHandlingAMessageNoRouteDeliversIsRefused() {
        Hfsm<MatchState> tree = Hfsm.of(
                List.of(
                        StateDef.<MatchState>root(UnitAi.INDIVIDUAL)
                                .initial(UnitAi.IDLE)
                                .build(),
                        StateDef.<MatchState>child(UnitAi.IDLE, UnitAi.INDIVIDUAL)
                                .on(StatsChanged.class, (ctx, m) -> {})
                                .build()),
                (state, handle, from, to) -> {});
        MatchState state = KernelAccess.state(SimHarness.builder().build().core());

        assertThatThrownBy(() -> new UnitAiSystem(state, tree))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("StatsChanged");
    }
}
