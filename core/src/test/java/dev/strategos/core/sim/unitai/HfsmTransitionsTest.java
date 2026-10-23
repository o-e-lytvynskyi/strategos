package dev.strategos.core.sim.unitai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.AiStateStore;
import dev.strategos.core.sim.UnitState;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * UNIT-02.1: the framework's rules on a test tree: bubbling and overriding handlers, transitions run once the handler
 * returns, the order of leave and enter hooks across nested states, chained and refused transitions, data words,
 * thinks, and messages dispatched from inside a handler. The unit tree's rows are {@link UnitTreeTransitionsTest}'s.
 */
class HfsmTransitionsTest {
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
