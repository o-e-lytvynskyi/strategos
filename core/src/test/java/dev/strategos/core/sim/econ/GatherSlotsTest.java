package dev.strategos.core.sim.econ;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.NoticeKind;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.Notice;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.unitai.UnitAi;
import dev.strategos.core.testkit.TreeFixtures.Species;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * ECO-05.4 in L1: thirty workers sent to one small forest of three oaks (24 slots) never put more workers on a node
 * than it has slots, never two on one slot, and never one at a node without its claim; the six that find no room stand
 * idle with a notice at once. The forest is felled within the two minutes the test runs, so in the end all stand idle.
 */
class GatherSlotsTest {
    @Test
    void thirtyWorkersOneForest() {
        EconScene e = EconScene.flat(80, 64)
                .tree(Species.OAK, 16, 20)
                .tree(Species.OAK, 22, 26)
                .tree(Species.OAK, 16, 32)
                .build();
        e.civicCentre(0, 50, 20);
        int[] workers = e.workers(0, new Vec2d(36, 26), 30, 6);
        assertThat(workers).hasSize(30);
        e.gather(workers, 16, 2, 20);
        NodeStore nodes = e.s.nodes();
        e.h.step();
        assertThat(e.h.events(Notice.class).stream().filter(n -> n.kind() == NoticeKind.IDLE_WORKER))
                .as("the six without a slot, at once")
                .hasSize(6);
        long sent = java.util.Arrays.stream(workers)
                .filter(w -> e.s.ai().code(Handle.index(w)) == UnitAi.GATHER_APPROACHING.code())
                .count();
        assertThat(sent).as("the 24 with one").isEqualTo(24);
        int busiest = 0;
        for (int t = 0; t < 2_400; t++) {
            e.h.step();
            for (int id = 1; id <= nodes.count(); id++) {
                Set<Integer> holders = new HashSet<>();
                for (int slot = 0; slot < nodes.slotCount(id); slot++) {
                    int c = nodes.claim(id, slot);
                    if (c != 0) {
                        assertThat(holders.add(c))
                                .as("tick %d: one slot per worker", e.h.tick())
                                .isTrue();
                    }
                }
                int working = 0;
                for (int w : workers) {
                    int i = Handle.index(w);
                    if (e.s.entities().isAlive(w)
                            && e.s.ai().state(i) == UnitState.GATHERING
                            && e.s.orders().target(i) == id) {
                        working++;
                        assertThat(holders)
                                .as("tick %d: a worker at work holds its slot", e.h.tick())
                                .contains(w);
                    }
                }
                assertThat(working).as("tick %d, node %d", e.h.tick(), id).isLessThanOrEqualTo(nodes.slotCount(id));
                busiest = Math.max(busiest, working);
            }
        }
        assertThat(busiest).as("some tree was worked by a full ring").isEqualTo(8);
        for (int id = 1; id <= nodes.count(); id++) {
            assertThat(nodes.exhausted(id)).as("the forest felled").isTrue();
        }
    }
}
