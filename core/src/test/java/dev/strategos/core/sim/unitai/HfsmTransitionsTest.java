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
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.AiStateStore;
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
 * change only a think makes, a tick); then the framework's rules on a test tree: bubbling and overriding handlers,
 * transitions run once the handler returns, the order of leave and enter hooks across nested states, chained and
 * refused transitions, data words, thinks, and messages dispatched from inside a handler.
 */
class HfsmTransitionsTest {

    // ---- The unit tree ----

    /**
     * One spearman of a real match and an enemy spearman, in the row's start state: IDLE; WALKING by an order and a
     * tick; any other set as data, as a save restores it, with the enemy placed for it. A row publishes on the bus
     * between ticks, as a system would, or steps a tick.
     */
    private static final class Unit {
        final SimHarness h = SimHarness.builder().build();
        final int handle = spearman(h, 0, new Vec2d(4, 8));
        final int enemy = spearman(h, 1, new Vec2d(28, 28)); // 31 blocks off: out of sight
        final MatchState state = KernelAccess.state(h.core());
        final List<OrderFinished> finished = new ArrayList<>();

        Unit(StateId from) {
            state.bus().subscribe(OrderFinished.class, finished::add);
            int i = index();
            if (from.equals(UnitAi.WALKING)) {
                h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(handle), new Vec2d(20, 8)));
                h.step();
            } else if (from.equals(UnitAi.WALKING_AND_FIGHTING)) {
                state.orders().replace(i, OrderKind.ATTACK_MOVE, 20, 8, 0, 0);
                state.ai().set(i, from);
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

    private static int spearman(SimHarness h, int faction, Vec2d at) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, new FactionSlot(faction), at, 1, 0));
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
        return Arguments.of(name, from, message, to, effects);
    }

    /** A move order to (20, 20) set as the move rule sets it, then announced. */
    private static BusMessage newMove(Unit u) {
        u.state.orders().replaceMove(u.index(), 20, 20);
        return new OrderReceived(u.handle);
    }

    /** The player's attack on the enemy, 6 blocks off, set as the attack rule sets it, then announced. */
    private static BusMessage newAttack(Unit u) {
        u.place(u.enemy, 10, 8);
        u.state.orders().replace(u.index(), OrderKind.ATTACK, 10, 8, 0, 0, u.enemy);
        return new OrderReceived(u.handle);
    }

    /** An attack-move to (20, 20), set as its rule sets it, then announced. */
    private static BusMessage newAttackMove(Unit u) {
        u.state.orders().replace(u.index(), OrderKind.ATTACK_MOVE, 20, 20, 0, 0);
        return new OrderReceived(u.handle);
    }

    /** A stop as the stop rule makes it: the order cleared, then announced. */
    private static BusMessage stop(Unit u) {
        u.state.orders().clear(u.index());
        return new StopOrdered(u.handle);
    }

    /** The enemy's blow, as the COMBAT phase tells of one the unit lived through. */
    private static BusMessage blow(Unit u) {
        return new Attacked(u.handle, u.enemy);
    }

    /** The enemy stands in reach and the unit arrives. */
    private static BusMessage arrivedInReach(Unit u) {
        u.place(u.enemy, 5.2, 8);
        return new Arrived(u.handle);
    }

    private static void nothingElse(Unit u) {
        assertThat(u.finished).isEmpty();
        assertThat(u.state.paths().repath(u.index())).isFalse();
    }

    private static void orderKept(Unit u) {
        assertThat(u.state.orders().kind(u.index())).isEqualTo(OrderKind.MOVE);
        nothingElse(u);
    }

    private static Consumer<Unit> kept(int kind) {
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

    private static Consumer<Unit> orderFinished(boolean failed) {
        return u -> {
            assertThat(u.state.orders().kind(u.index())).isEqualTo(OrderKind.NONE);
            assertThat(u.finished).containsExactly(new OrderFinished(u.handle, failed));
        };
    }

    static Stream<Arguments> unitRows() {
        StateId idle = UnitAi.IDLE;
        StateId walking = UnitAi.WALKING;
        StateId fighting = UnitAi.WALKING_AND_FIGHTING;
        StateId approaching = UnitAi.APPROACHING;
        StateId attacking = UnitAi.ATTACKING;
        StateId chasing = UnitAi.CHASING;
        return Stream.of(
                row("an order starts walking (the root's handler)", idle, HfsmTransitionsTest::newMove, walking, u -> {
                    assertThat(u.state.orders().targetZ(u.index())).isEqualTo(20);
                    nothingElse(u);
                }),
                row(
                        "a new order while walking carries on toward its target",
                        walking,
                        HfsmTransitionsTest::newMove,
                        walking,
                        u -> {
                            assertThat(u.state.orders().targetZ(u.index())).isEqualTo(20);
                            nothingElse(u);
                        }),
                row(
                        "a stop while walking idles (the root's handler, inherited)",
                        walking,
                        HfsmTransitionsTest::stop,
                        idle,
                        HfsmTransitionsTest::nothingElse),
                row(
                        "a stop while idle does nothing (IDLE's own handler)",
                        idle,
                        HfsmTransitionsTest::stop,
                        idle,
                        HfsmTransitionsTest::nothingElse),
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
                        HfsmTransitionsTest::orderKept),
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
                        HfsmTransitionsTest::orderKept),
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
                        HfsmTransitionsTest::orderKept),
                row(
                        "a blow while walking is no reason to stop (a player's move)",
                        walking,
                        HfsmTransitionsTest::blow,
                        walking,
                        HfsmTransitionsTest::orderKept),
                row(
                        "an arrival while idle is dropped at the root",
                        idle,
                        u -> new Arrived(u.handle),
                        idle,
                        HfsmTransitionsTest::nothingElse),
                row(
                        "the last stuck rung while idle is dropped",
                        idle,
                        u -> new Stuck(u.handle, Stuck.GIVE_UP),
                        idle,
                        HfsmTransitionsTest::nothingElse),
                row(
                        "an invalidated path while idle is dropped",
                        idle,
                        u -> new PathInvalidated(u.handle),
                        idle,
                        HfsmTransitionsTest::nothingElse),
                // CMB-02 and ORD-04
                row(
                        "an attack order closes in",
                        idle,
                        HfsmTransitionsTest::newAttack,
                        approaching,
                        kept(OrderKind.ATTACK)),
                row(
                        "an attack while walking closes in",
                        walking,
                        HfsmTransitionsTest::newAttack,
                        approaching,
                        kept(OrderKind.ATTACK)),
                row(
                        "an attack-move walks and fights",
                        idle,
                        HfsmTransitionsTest::newAttackMove,
                        fighting,
                        kept(OrderKind.ATTACK_MOVE)),
                row(
                        "an attack-move while walking",
                        walking,
                        HfsmTransitionsTest::newAttackMove,
                        fighting,
                        kept(OrderKind.ATTACK_MOVE)),
                row("an idle unit hit hits back", idle, HfsmTransitionsTest::blow, approaching, engaged(0)),
                row(
                        "an attack-move's arrival finishes it",
                        fighting,
                        u -> new Arrived(u.handle),
                        idle,
                        orderFinished(false)),
                row(
                        "a move while walking and fighting",
                        fighting,
                        HfsmTransitionsTest::newMove,
                        walking,
                        kept(OrderKind.MOVE)),
                row(
                        "a unit walking and fighting hits back, its walk waiting",
                        fighting,
                        HfsmTransitionsTest::blow,
                        approaching,
                        engaged(1)),
                row(
                        "an attack while walking and fighting",
                        fighting,
                        HfsmTransitionsTest::newAttack,
                        approaching,
                        kept(OrderKind.ATTACK)),
                row(
                        "a stop while walking and fighting idles",
                        fighting,
                        HfsmTransitionsTest::stop,
                        idle,
                        HfsmTransitionsTest::nothingElse),
                row(
                        "a stop while closing in idles",
                        approaching,
                        HfsmTransitionsTest::stop,
                        idle,
                        HfsmTransitionsTest::nothingElse),
                row(
                        "a move while closing in",
                        approaching,
                        HfsmTransitionsTest::newMove,
                        walking,
                        kept(OrderKind.MOVE)),
                row(
                        "an attack-move while closing in",
                        approaching,
                        HfsmTransitionsTest::newAttackMove,
                        fighting,
                        kept(OrderKind.ATTACK_MOVE)),
                row(
                        "arriving in reach strikes",
                        approaching,
                        HfsmTransitionsTest::arrivedInReach,
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
                        HfsmTransitionsTest::blow,
                        approaching,
                        kept(OrderKind.ATTACK)),
                row(
                        "a stop while striking idles",
                        attacking,
                        HfsmTransitionsTest::stop,
                        idle,
                        HfsmTransitionsTest::nothingElse),
                row("a move while striking", attacking, HfsmTransitionsTest::newMove, walking, kept(OrderKind.MOVE)),
                row(
                        "an attack-move while striking",
                        attacking,
                        HfsmTransitionsTest::newAttackMove,
                        fighting,
                        kept(OrderKind.ATTACK_MOVE)),
                row(
                        "a new attack while striking closes in on it afresh",
                        attacking,
                        HfsmTransitionsTest::newAttack,
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
                        HfsmTransitionsTest::stop,
                        idle,
                        HfsmTransitionsTest::nothingElse),
                row("a move while chasing", chasing, HfsmTransitionsTest::newMove, walking, kept(OrderKind.MOVE)),
                row(
                        "an attack-move while chasing",
                        chasing,
                        HfsmTransitionsTest::newAttackMove,
                        fighting,
                        kept(OrderKind.ATTACK_MOVE)),
                row(
                        "a new attack while chasing closes in afresh",
                        chasing,
                        HfsmTransitionsTest::newAttack,
                        approaching,
                        kept(OrderKind.ATTACK)),
                row(
                        "catching up strikes",
                        chasing,
                        HfsmTransitionsTest::arrivedInReach,
                        attacking,
                        kept(OrderKind.ATTACK)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unitRows")
    void theUnitTree(
            String name,
            StateId from,
            Function<Unit, @Nullable BusMessage> message,
            StateId to,
            Consumer<Unit> effects) {
        Unit u = new Unit(from);

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

    // ---- The framework, on a test tree ----

    private static final StateId ROOT = new StateId(10, "root", null);
    private static final StateId A = new StateId(11, "a", UnitState.IDLE);
    private static final StateId B = new StateId(12, "b", null);
    private static final StateId B1 = new StateId(13, "b1", UnitState.WALKING);
    private static final StateId B2 = new StateId(14, "b2", UnitState.WALKING);
    private static final StateId C = new StateId(15, "c", UnitState.WALKING);
    /** Its enter hook moves on to A. */
    private static final StateId D = new StateId(16, "d", UnitState.WALKING);
    /** E and F enter each other forever. */
    private static final StateId E = new StateId(17, "e", UnitState.WALKING);

    private static final StateId F = new StateId(18, "f", UnitState.WALKING);
    /** Its leave hook asks for a transition, which is refused. */
    private static final StateId L = new StateId(19, "l", UnitState.WALKING);

    record Ping() implements AiMessage {}

    record Go(StateId target) implements AiMessage {}

    record Unhandled() implements AiMessage {}

    record Undeclared() implements AiMessage {}

    record Twice() implements AiMessage {}

    record Data(int data0, int data1) implements AiMessage {}

    /** Requests {@code target}, but first dispatches {@code next} to the same unit. */
    record Relay(StateId target, AiMessage next) implements AiMessage {}

    /** The test tree's host: what its hooks and handlers did, in order. */
    private static final class Log {
        final List<String> lines = new ArrayList<>();
        Hfsm.Machine<Log> machine;
    }

    /** Logs entering and leaving {@code id}, and the data words a leaving state holds once either is set. */
    private static StateDef.Builder<Log> logged(StateDef.Builder<Log> b, StateId id) {
        return b.enter(ctx -> ctx.host().lines.add("enter " + id)).leave(ctx -> {
            String words = ctx.data0() != 0 || ctx.data1() != 0 ? " " + ctx.data0() + "," + ctx.data1() : "";
            ctx.host().lines.add("leave " + id + words);
        });
    }

    /**
     * ROOT { A, B { B1, B2 }, C, D, E, F, L }: ROOT handles Ping, Go, Twice, Data and Relay and declares every state;
     * A overrides Ping and handles Undeclared; B thinks every 4 ticks, B2 every 2 with its own think, which goes to C
     * when its first word is 1.
     */
    private static Hfsm<Log> tree() {
        return Hfsm.of(
                List.of(
                        logged(StateDef.root(ROOT), ROOT)
                                .initial(A)
                                .on(Ping.class, (ctx, m) -> ctx.host().lines.add("root ping in " + ctx.leaf()))
                                .on(Go.class, (ctx, m) -> ctx.go(m.target()))
                                .on(Twice.class, (ctx, m) -> {
                                    ctx.go(A);
                                    ctx.go(C);
                                })
                                .on(Data.class, (ctx, m) -> ctx.setData(m.data0(), m.data1()))
                                .on(Relay.class, (ctx, m) -> {
                                    ctx.host().lines.add("relay");
                                    ctx.host().machine.dispatch(ctx.handle(), m.next());
                                    ctx.go(m.target());
                                    ctx.host().lines.add("relayed");
                                })
                                .transitions(ROOT, A, B, B1, B2, C, D, E, L)
                                .build(),
                        logged(StateDef.child(A, ROOT), A)
                                .on(Ping.class, (ctx, m) -> ctx.host().lines.add("a ping"))
                                .on(Undeclared.class, (ctx, m) -> ctx.go(C))
                                .build(),
                        logged(StateDef.child(B, ROOT), B)
                                .initial(B1)
                                .thinkEvery(4)
                                .think(ctx -> ctx.host().lines.add("b thinks for " + ctx.leaf() + " #" + ctx.unit()))
                                .build(),
                        logged(StateDef.child(B1, B), B1).build(),
                        logged(StateDef.child(B2, B), B2)
                                .thinkEvery(2)
                                .think(ctx -> {
                                    ctx.host().lines.add("b2 thinks #" + ctx.unit());
                                    if (ctx.data0() == 1) {
                                        ctx.go(C);
                                    }
                                })
                                .transitions(C)
                                .build(),
                        logged(StateDef.child(C, ROOT), C).build(),
                        logged(StateDef.child(D, ROOT), D)
                                .enter(ctx -> {
                                    ctx.host().lines.add("enter d");
                                    ctx.go(A);
                                })
                                .transitions(A)
                                .build(),
                        StateDef.<Log>child(E, ROOT)
                                .enter(ctx -> ctx.go(F))
                                .transitions(F)
                                .build(),
                        StateDef.<Log>child(F, ROOT)
                                .enter(ctx -> ctx.go(E))
                                .transitions(E)
                                .build(),
                        StateDef.<Log>child(L, ROOT)
                                .leave(ctx -> ctx.go(A))
                                .transitions(A)
                                .build()),
                (log, handle, from, to) -> log.lines.add(from + " -> " + to));
    }

    /** A test tree, its log and store, and units 0 to 7 placed in {@code at} without hooks. */
    private static final class Rig {
        final Log log = new Log();
        final AiStateStore store = new AiStateStore();
        final Hfsm.Machine<Log> machine;

        Rig(StateId at) {
            machine = tree().machine(log, store);
            log.machine = machine;
            store.ensureCapacity(8);
            for (int i = 0; i < 8; i++) {
                store.reset(i, at);
            }
        }

        static int unit(int index) {
            return Handle.of(index, 1);
        }

        List<String> send(AiMessage message) {
            log.lines.clear();
            machine.dispatch(unit(0), message);
            return log.lines;
        }

        StateId leaf(int index) {
            for (StateId s : List.of(A, B1, B2, C, D, E, F, L)) {
                if (s.code() == store.code(index)) {
                    return s;
                }
            }
            throw new AssertionError("unit " + index + " in no leaf: code " + store.code(index));
        }
    }

    @Test
    void aMessageGoesUpToTheNearestStateThatHandlesIt() {
        assertThat(new Rig(B1).send(new Ping())).as("B1 and B pass it on").containsExactly("root ping in b1");
        assertThat(new Rig(A).send(new Ping())).as("A overrides the root").containsExactly("a ping");
    }

    @Test
    void aMessageNoStateHandlesIsDropped() {
        Rig rig = new Rig(B2);
        assertThat(rig.send(new Unhandled())).isEmpty();
        assertThat(rig.leaf(0)).isEqualTo(B2);
    }

    @Test
    void aTransitionRunsOnceTheHandlerReturns() {
        Rig rig = new Rig(A);
        assertThat(rig.send(new Relay(C, new Ping())))
                .as("the relayed Ping waits for the handler and its transition, then finds C")
                .containsExactly("relay", "relayed", "leave a", "enter c", "a -> c", "root ping in c");
        assertThat(rig.leaf(0)).isEqualTo(C);
    }

    static Stream<Arguments> transitions() {
        return Stream.of(
                Arguments.of(A, B, List.of("leave a", "enter b", "enter b1", "a -> b1")),
                Arguments.of(A, B2, List.of("leave a", "enter b", "enter b2", "a -> b2")),
                Arguments.of(B1, B2, List.of("leave b1", "enter b2", "b1 -> b2")),
                Arguments.of(B1, C, List.of("leave b1", "leave b", "enter c", "b1 -> c")),
                Arguments.of(B2, B, List.of("leave b2", "leave b", "enter b", "enter b1", "b2 -> b1")),
                Arguments.of(B1, B1, List.of("leave b1", "enter b1", "b1 -> b1")),
                Arguments.of(
                        B1, ROOT, List.of("leave b1", "leave b", "leave root", "enter root", "enter a", "b1 -> a")));
    }

    @ParameterizedTest(name = "{0} to {1}")
    @MethodSource("transitions")
    void aTransitionLeavesUpToTheStateItStaysInAndEntersDownToALeaf(StateId from, StateId to, List<String> lines) {
        Rig rig = new Rig(from);
        assertThat(rig.send(new Go(to))).containsExactlyElementsOf(lines);
        assertThat(rig.leaf(0)).isEqualTo(to.equals(ROOT) ? A : to.equals(B) ? B1 : to);
    }

    @Test
    void anEnterHooksRequestRunsOnceItsTransitionIsComplete() {
        Rig rig = new Rig(C);
        assertThat(rig.send(new Go(D))).containsExactly("leave c", "enter d", "c -> d", "leave d", "enter a", "d -> a");
        assertThat(rig.leaf(0)).isEqualTo(A);
    }

    @Test
    void enterHooksRequestingEachOtherFailAfterTheChainLimit() {
        Rig rig = new Rig(A);
        assertThatThrownBy(() -> rig.send(new Go(E)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("made " + Hfsm.MAX_CHAIN + " transitions in a row");
    }

    @Test
    void aStateRequestsOnlyTheTransitionsItDeclares() {
        Rig rig = new Rig(A);
        assertThatThrownBy(() -> rig.send(new Undeclared()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("state a requested a transition to c, which it does not declare");
        assertThat(rig.leaf(0)).isEqualTo(A);
    }

    @Test
    void twoRequestsInOneHandlerFail() {
        assertThatThrownBy(() -> new Rig(B1).send(new Twice()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requested transitions to a and c at once");
    }

    @Test
    void aLeaveHookCannotRequestATransition() {
        assertThatThrownBy(() -> new Rig(L).send(new Go(C)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("state l is being left");
    }

    @Test
    void theDataWordsAreTheLeafsAndEveryTransitionClearsThem() {
        Rig rig = new Rig(B1);
        rig.send(new Data(3, 4));
        assertThat(rig.store.data0(0)).isEqualTo(3);

        assertThat(rig.send(new Go(B2))).as("the leave hook still sees them").startsWith("leave b1 3,4");
        assertThat(rig.store.data0(0)).isZero();
        assertThat(rig.store.data1(0)).isZero();
        assertThat(rig.store.plain(0)).isTrue();
    }

    @Test
    void unitsThinkOnceEveryThinkPeriodOfTheirLeafStaggeredByIndex() {
        Rig rig = new Rig(B1);
        rig.store.reset(4, B2);
        List<String> seen = new ArrayList<>();
        for (long tick = 0; tick < 8; tick++) {
            for (int i = 0; i < 5; i++) {
                rig.log.lines.clear();
                rig.machine.think(tick, Rig.unit(i));
                for (String line : rig.log.lines) {
                    seen.add(tick + ": " + line);
                }
            }
        }
        assertThat(seen)
                .as("B1 thinks B's think every 4 ticks when (tick + index) mod 4 = 0; B2 its own every 2")
                .containsExactly(
                        "0: b thinks for b1 #0",
                        "0: b2 thinks #4",
                        "1: b thinks for b1 #3",
                        "2: b thinks for b1 #2",
                        "2: b2 thinks #4",
                        "3: b thinks for b1 #1",
                        "4: b thinks for b1 #0",
                        "4: b2 thinks #4",
                        "5: b thinks for b1 #3",
                        "6: b thinks for b1 #2",
                        "6: b2 thinks #4",
                        "7: b thinks for b1 #1");
        assertThat(rig.machine.thinks()).isTrue();
    }

    @Test
    void aThinkMayRequestATransition() {
        Rig rig = new Rig(B2);
        rig.machine.dispatch(Rig.unit(0), new Data(1, 0));
        rig.log.lines.clear();

        rig.machine.think(0, Rig.unit(0));

        assertThat(rig.log.lines).containsExactly("b2 thinks #0", "leave b2 1,0", "leave b 1,0", "enter c", "b2 -> c");
        assertThat(rig.leaf(0)).isEqualTo(C);
    }

    @Test
    void aMachineRunsTheUnitsItIsGivenOnly() {
        Rig rig = new Rig(A);
        rig.machine.dispatch(Rig.unit(3), new Go(C));
        assertThat(rig.leaf(3)).isEqualTo(C);
        assertThat(rig.leaf(0)).isEqualTo(A);
    }
}
