package dev.strategos.core.sim.econ;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.NoticeKind;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.Notice;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.order.OrderKind;
import org.junit.jupiter.api.Test;

/**
 * ECO-05.2 in L1: a worker whose node runs out takes the nearest node of the same subtype within 24 blocks of it, and
 * otherwise takes home what it carries and stands idle with a notice.
 */
class GatherRetargetTest {
    /** Bushes of 15 food: a load and a half each. The centre is east, at x 40..50. */
    private static EconScene scene(int secondBushX) {
        EconScene e = EconScene.flat(64, 48)
                .bush(16, 24, 15, 0)
                .bush(secondBushX, 24, 15, 0)
                .build();
        e.civicCentre(0, 40, 19);
        return e;
    }

    @Test
    void theNearestBushWithin24BlocksTakesOver() {
        EconScene e = scene(16 + 10);
        int worker = e.workers(0, new Vec2d(30, 30), 1, 0)[0];
        int first = e.s.nodes().holding(NodeStore.pack(16, 1, 24));
        int second = e.s.nodes().holding(NodeStore.pack(26, 1, 24));
        e.gather(new int[] {worker}, 16, 1, 24);
        int i = e.index(worker);
        assertThat(e.h.runUntil(() -> e.s.nodes().exhausted(first), 5_000)).isTrue();
        e.h.step();
        assertThat(e.s.orders().kind(i)).isEqualTo(OrderKind.GATHER);
        assertThat(e.s.orders().target(i)).as("on to the next bush").isEqualTo(second);
        assertThat(e.s.carry().amount(i)).as("the half load kept").isEqualTo(5_000);
        assertThat(e.h.runUntil(() -> e.gathered(0, EconScene.FOOD) == 20_000, 5_000))
                .isTrue();
        assertThat(e.h.events(Notice.class)).noneMatch(n -> n.kind() == NoticeKind.IDLE_WORKER);
    }

    @Test
    void withNoneInReachTheLoadGoesHomeAndTheWorkerIdlesWithANotice() {
        EconScene e = scene(16 + 25); // 25 blocks: out of reach
        int worker = e.workers(0, new Vec2d(30, 30), 1, 0)[0];
        int first = e.s.nodes().holding(NodeStore.pack(16, 1, 24));
        e.gather(new int[] {worker}, 16, 1, 24);
        int i = e.index(worker);
        assertThat(e.h.runUntil(() -> e.s.nodes().exhausted(first), 5_000)).isTrue();
        assertThat(e.h.runUntil(() -> e.s.ai().state(i) == UnitState.IDLE, 5_000))
                .isTrue();
        assertThat(e.gathered(0, EconScene.FOOD))
                .as("both loads home, the half one too")
                .isEqualTo(15_000);
        assertThat(e.s.carry().amount(i)).isZero();
        assertThat(e.s.orders().kind(i)).isEqualTo(OrderKind.NONE);
        assertThat(e.h.events(Notice.class))
                .filteredOn(n -> n.kind() == NoticeKind.IDLE_WORKER)
                .singleElement()
                .satisfies(n -> {
                    assertThat(n.handle()).isEqualTo(worker);
                    assertThat(n.faction().value()).isZero();
                });
        assertThat(e.s.carry().claimed(i)).as("no slot held").isZero();
    }

    @Test
    void aTreeFelledToTheLastLogSendsItsWorkerToTheNextTree() {
        EconScene e = EconScene.flat(64, 48)
                .tree(dev.strategos.core.testkit.TreeFixtures.Species.OAK, 16, 24)
                .tree(dev.strategos.core.testkit.TreeFixtures.Species.BIRCH, 16, 34)
                .build();
        e.civicCentre(0, 40, 19);
        int worker = e.workers(0, new Vec2d(30, 24), 1, 0)[0];
        int oak = e.s.nodes().holding(NodeStore.pack(16, 1, 24));
        int birch = e.s.nodes().holding(NodeStore.pack(16, 1, 34));
        e.gather(new int[] {worker}, 16, 1, 24);
        int i = e.index(worker);
        assertThat(e.h.runUntil(() -> e.s.nodes().exhausted(oak), 20_000)).isTrue();
        // its last load was full, so it takes that home first, then makes for the birch
        assertThat(e.h.runUntil(() -> e.s.orders().target(i) == birch, 1_000)).isTrue();
        assertThat(e.gathered(0, EconScene.WOOD)).as("the whole oak home").isEqualTo(100_000);
        assertThat(e.s.nodes().blocks(oak).length - e.s.nodes().gone(oak))
                .as("every log of the oak felled")
                .isZero();
    }
}
