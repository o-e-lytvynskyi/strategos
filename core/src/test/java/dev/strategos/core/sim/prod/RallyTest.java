package dev.strategos.core.sim.prod;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.SetRally;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.TreeFixtures;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** PROD-03.1 in L1: workers rallied to a tree walk there and gather on arrival; a rally on the ground walks there. */
class RallyTest {
    @Test
    void workersRalliedToATreeGatherOnArrival() {
        VoxelWorld w = VoxelFixtures.flat(64, 64, 32, 1);
        TreeFixtures.draw(w, TreeFixtures.Species.OAK, 30, 1, 30);
        ProdScene p = ProdScene.of(1_000, 0, MatchRules.DEFAULTS, w, SimHarness.DEFAULT_SEED);
        p.h.submit(ProdScene.P0, new SetRally(UnitSet.of(p.centre), 30, 3, 30)); // a log of the oak
        p.h.step();
        assertThat(p.h.results().get(p.h.results().size() - 1).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(p.s.buildings().rallyKind(p.centre)).isEqualTo(BuildingStore.RALLY_NODE);
        p.train();
        p.train();
        List<Integer> made = new ArrayList<>();
        for (int t = 0; t < 900; t++) {
            p.h.step().events().stream()
                    .filter(UnitCreated.class::isInstance)
                    .forEach(e -> made.add(((UnitCreated) e).handle()));
        }
        assertThat(made).hasSize(2);
        for (int unit : made) {
            int i = Handle.index(unit);
            assertThat(p.s.orders().kind(i)).isEqualTo(OrderKind.GATHER);
            assertThat(p.s.orders().target(i)).isEqualTo(p.s.buildings().rallyTarget(p.centre));
        }
        assertThat(p.s.treasury().gathered(0, 1)).as("their wood came home").isPositive();

        p.h.submit(ProdScene.P0, new SetRally(UnitSet.of(p.centre), 50, 1, 10));
        p.train();
        UnitCreated last = null;
        for (int t = 0; t < 700; t++) { // 10 s of training, 44 blocks of walk
            for (var e : p.h.step().events()) {
                if (e instanceof UnitCreated c) {
                    last = c;
                }
            }
        }
        assertThat(last).isNotNull();
        int i = Handle.index(last.handle());
        assertThat(p.s.ai().state(i)).isEqualTo(UnitState.IDLE);
        assertThat(Math.hypot(p.s.motion().x(i) - 50.5, p.s.motion().z(i) - 10.5))
                .isLessThan(1.0);
    }
}
