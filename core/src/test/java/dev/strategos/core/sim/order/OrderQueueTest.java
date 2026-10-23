package dev.strategos.core.sim.order;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.testkit.SimHarness;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * ORD-02.1 (04 §7.4, spec §5.9): queue semantics, table-driven. {@code REPLACE} leaves the new order alone, {@code QUEUE}
 * appends, {@code PUSH_FRONT} goes before the current order, which resumes after it, {@code finishOrder} pops, and the
 * 17th order is refused with {@code QUEUE_FULL}. Through the Move rule: an idle unit starts a queued order at once and a
 * walking one keeps its current order; a command to several units gives them one new group id; the ring keeps its
 * order across wrap-arounds and a save.
 */
class OrderQueueTest {
    private record Case(String name, Consumer<OrderQueues> ops, List<Double> targets) {}

    @Test
    void queueSemantics() {
        List<Case> cases = List.of(
                new Case("replace on empty", q -> replace(q, 1), List.of(1.0)),
                new Case(
                        "replace clears",
                        q -> {
                            replace(q, 1);
                            queue(q, 2);
                            queue(q, 3);
                            replace(q, 4);
                        },
                        List.of(4.0)),
                new Case("queue on empty", q -> queue(q, 1), List.of(1.0)),
                new Case(
                        "queue appends",
                        q -> {
                            replace(q, 1);
                            queue(q, 2);
                            queue(q, 3);
                        },
                        List.of(1.0, 2.0, 3.0)),
                new Case(
                        "push front goes first",
                        q -> {
                            replace(q, 1);
                            queue(q, 2);
                            pushFront(q, 9);
                        },
                        List.of(9.0, 1.0, 2.0)),
                new Case(
                        "pop resumes the next",
                        q -> {
                            replace(q, 1);
                            pushFront(q, 9);
                            q.pop(0);
                        },
                        List.of(1.0)),
                new Case(
                        "pop the last empties",
                        q -> {
                            replace(q, 1);
                            q.pop(0);
                        },
                        List.of()),
                new Case("pop on empty", q -> q.pop(0), List.of()),
                new Case(
                        "clear",
                        q -> {
                            replace(q, 1);
                            queue(q, 2);
                            q.clear(0);
                        },
                        List.of()),
                new Case(
                        "wrap-around",
                        q -> {
                            for (int k = 0; k < 40; k++) {
                                queue(q, k);
                                if (k % 3 != 0) {
                                    q.pop(0);
                                }
                            }
                        },
                        expectedWrap()),
                new Case(
                        "push front wraps backwards",
                        q -> {
                            for (int k = 1; k <= 5; k++) {
                                pushFront(q, k);
                            }
                        },
                        List.of(5.0, 4.0, 3.0, 2.0, 1.0)));
        for (Case c : cases) {
            OrderQueues q = queues();
            c.ops().accept(q);

            assertThat(targets(q)).as(c.name()).isEqualTo(c.targets());
            assertThat(q.kind(0)).as(c.name()).isEqualTo(c.targets().isEmpty() ? OrderKind.NONE : OrderKind.MOVE);
        }
    }

    @Test
    void sixteenOrdersAtMost() {
        OrderQueues q = queues();
        replace(q, 0);
        for (int k = 1; k < OrderQueues.MAX; k++) {
            assertThat(q.queue(0, OrderKind.MOVE, k, 0, 0, 0)).isTrue();
        }

        assertThat(q.size(0)).isEqualTo(OrderQueues.MAX);
        assertThat(q.queue(0, OrderKind.MOVE, 99, 0, 0, 0))
                .as("the 17th queued")
                .isFalse();
        assertThat(q.pushFront(0, OrderKind.MOVE, 99, 0, 0, 0))
                .as("the 17th pushed")
                .isFalse();
        assertThat(q.size(0)).isEqualTo(OrderQueues.MAX);
        assertThat(q.targetX(0)).as("nothing changed").isEqualTo(0);
    }

    @Test
    void aSaveKeepsTheQueue() {
        OrderQueues q = queues();
        replace(q, 1);
        queue(q, 2);
        pushFront(q, 3);
        OrderQueues back = queues();

        back.restore(0, q.targetX(0), q.targetZ(0), q.saved(0));

        assertThat(targets(back)).isEqualTo(targets(q));
        OrderQueues single = queues();
        replace(single, 1);
        assertThat(single.saved(0)).as("M0's single order").isEqualTo(OrderQueues.Saved.NONE);
    }

    @Test
    void theMoveRuleQueuesPushesAndRefusesTheSeventeenth() {
        SimHarness h = SimHarness.builder().build();
        int unit = h.spawn(new FactionSlot(0), new Vec2d(4.5, 4.5), 1, 0)[0];
        MatchState s = KernelAccess.state(h.core());
        int i = Handle.index(unit);

        assertThat(move(h, unit, 20.5, QueueMode.QUEUE).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(h.queries().unit(unit).state())
                .as("an idle unit starts a queued order")
                .isEqualTo(UnitState.WALKING);
        move(h, unit, 24.5, QueueMode.QUEUE);
        assertThat(s.orders().targetX(i)).as("a walking unit keeps its order").isEqualTo(20.5);
        move(h, unit, 30.5, QueueMode.PUSH_FRONT); // 26 blocks away: it arrives nowhere during this test
        assertThat(targetsOf(s.orders(), i)).containsExactly(30.5, 20.5, 24.5);
        for (int k = 3; k < OrderQueues.MAX; k++) {
            move(h, unit, 28.5, QueueMode.QUEUE);
        }
        CommandResult full = move(h, unit, 29.5, QueueMode.QUEUE);
        assertThat(full.outcome()).isEqualTo(CommandOutcome.REJECTED);
        assertThat(full.firstReason()).isEqualTo(RejectReason.QUEUE_FULL);
        assertThat(move(h, unit, 29.5, QueueMode.PUSH_FRONT).firstReason()).isEqualTo(RejectReason.QUEUE_FULL);
        assertThat(move(h, unit, 29.5, QueueMode.REPLACE).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(targetsOf(s.orders(), i)).containsExactly(29.5);
    }

    @Test
    void aCommandToSeveralUnitsGivesThemOneNewGroup() {
        SimHarness h = SimHarness.builder().build();
        int[] units = h.spawn(new FactionSlot(0), new Vec2d(8, 8), 3, 2);
        MatchState s = KernelAccess.state(h.core());

        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(units), new Vec2d(20, 20)));
        h.step();
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(units[0], units[1]), new Vec2d(24, 24)));
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(units[2]), new Vec2d(4, 24)));
        h.step();

        int first = MatchState.FIRST_GROUP;
        assertThat(s.orders().group(Handle.index(units[0]))).isEqualTo(first + 1);
        assertThat(s.orders().group(Handle.index(units[1]))).isEqualTo(first + 1);
        assertThat(s.orders().group(Handle.index(units[2])))
                .as("an order to one unit alone")
                .isZero();
        assertThat(s.nextGroup()).isEqualTo(first + 2);
    }

    private static CommandResult move(SimHarness h, int unit, double x, QueueMode mode) {
        int seq = h.submit(PlayerSlot.ADMIN, new Move(UnitSet.of(unit), new Vec2d(x, 4.5), mode, 0));
        h.step();
        return h.results().stream()
                .filter(r -> r.clientSeq() == seq)
                .findFirst()
                .orElseThrow();
    }

    /** The wrap-around case on a plain list: append k, then drop the first unless k is a multiple of 3. */
    private static List<Double> expectedWrap() {
        List<Double> expected = new ArrayList<>();
        for (int k = 0; k < 40; k++) {
            expected.add((double) k);
            if (k % 3 != 0) {
                expected.remove(0);
            }
        }
        return expected;
    }

    private static OrderQueues queues() {
        OrderQueues q = new OrderQueues();
        q.ensureCapacity(1);
        return q;
    }

    private static void replace(OrderQueues q, double x) {
        q.replace(0, OrderKind.MOVE, x, 0, 0, 0);
    }

    private static void queue(OrderQueues q, double x) {
        q.queue(0, OrderKind.MOVE, x, 0, 0, 0);
    }

    private static void pushFront(OrderQueues q, double x) {
        q.pushFront(0, OrderKind.MOVE, x, 0, 0, 0);
    }

    private static List<Double> targets(OrderQueues q) {
        return targetsOf(q, 0);
    }

    private static List<Double> targetsOf(OrderQueues q, int i) {
        List<Double> out = new ArrayList<>();
        for (int k = 0; k < q.size(i); k++) {
            out.add(q.order(i, k).x());
        }
        return out;
    }
}
