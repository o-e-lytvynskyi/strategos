package dev.strategos.core.sim.econ;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.WorldEffect;
import dev.strategos.core.sim.nav.MaterialKind;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ECO-07 in L1, on a 3 × 3 rock hill three blocks above a flat floor: it is one outcrop of 27 blocks; mined out, every
 * block above the floor has gone, top-down, and none at or below it (1); a worker mines at the sheet's 0.4 stone a
 * second, 20 thousandths a tick (2); and with three at work no unit stands below the floor at any tick (3).
 */
class OutcropTest {
    private static final int STONE = 2;

    private static EconScene hill() {
        EconScene e = EconScene.flat(48, 48).rock(10, 10, 12, 12, 3).build();
        e.civicCentre(0, 30, 30);
        return e;
    }

    private static int outcrop(EconScene e) {
        for (int id = 1; id <= e.s.nodes().count(); id++) {
            if (e.s.nodes().kind(id) == MaterialKind.STONE) {
                return id;
            }
        }
        throw new AssertionError("no outcrop found");
    }

    @Test
    void aMinedOutcropLeavesItsFloor() {
        EconScene e = hill();
        int node = outcrop(e);
        assertThat(e.s.nodes().blocks(node)).as("the rock above the floor").hasSize(27);
        int[] workers = e.workers(0, new Vec2d(8.5, 11.5), 3, 1);
        e.gather(workers, 11, 3, 11);
        List<Integer> removedY = new ArrayList<>();
        for (int t = 0; t < 40_000 && !e.s.nodes().exhausted(node); t++) {
            TickOutputs out = e.h.step();
            for (OutputEvent ev : out.events()) {
                if (ev instanceof WorldEffect w && WorldEffect.AIR.equals(w.state()) && w.site() == node) {
                    removedY.add(w.y());
                }
            }
        }
        assertThat(e.s.nodes().exhausted(node)).as("mined out").isTrue();
        assertThat(removedY).hasSize(27).allSatisfy(y -> assertThat(y).isBetween(1, 3));
        for (int k = 1; k < removedY.size(); k++) {
            assertThat(removedY.get(k)).as("top-down").isLessThanOrEqualTo(removedY.get(k - 1));
        }
        for (int x = 10; x <= 12; x++) {
            for (int z = 10; z <= 12; z++) {
                assertThat(e.s.materialAt(x, 1, z))
                        .as("no rock left at %d, %d", x, z)
                        .isNull();
            }
        }
    }

    @Test
    void theRateIsTheSheets() {
        EconScene e = hill();
        int[] worker = e.workers(0, new Vec2d(9.5, 11.5), 1, 0);
        e.gather(worker, 11, 3, 11);
        int working = 0;
        for (int t = 0; t < 2_000 && working < 100; t++) {
            e.h.step();
            if (e.h.core().queries().unit(worker[0]).state() == dev.strategos.core.sim.UnitState.GATHERING) {
                working++;
            }
        }
        assertThat(working).isEqualTo(100);
        assertThat(e.s.carry().amount(e.index(worker[0])))
                .as("0.4 a second, 20 thousandths a tick, from the tick after it took up the work")
                .isEqualTo(20L * (working - 1));
        assertThat(e.s.carry().resource(e.index(worker[0]))).isEqualTo(STONE);
    }

    @Test
    void noUnitEndsBelowTheFloor() {
        EconScene e = hill();
        int node = outcrop(e);
        int[] workers = e.workers(0, new Vec2d(8.5, 11.5), 3, 1);
        e.gather(workers, 11, 3, 11);
        for (int t = 0; t < 40_000 && !e.s.nodes().exhausted(node); t++) {
            e.h.step();
            for (int w : workers) {
                UnitView u = e.h.core().queries().unit(w);
                assertThat(u.y()).as("unit %d at tick %d", w, t).isGreaterThanOrEqualTo(1.0 - 1e-9);
            }
        }
        assertThat(e.s.nodes().exhausted(node)).isTrue();
    }
}
