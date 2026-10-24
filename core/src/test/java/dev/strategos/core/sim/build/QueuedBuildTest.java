package dev.strategos.core.sim.build;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Build;
import dev.strategos.core.protocol.CancelBuilding;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

/**
 * ORD-02 with BLD-09: a build queued behind a walk, on a foundation cancelled while the unit walks, ends as the walk
 * does, the unit idle with nothing queued, and the match steps on.
 */
class QueuedBuildTest {
    @Test
    void aQueuedBuildOnACancelledFoundationEndsQuietly() {
        SimHarness h = ConstructionTest.match();
        MatchState s = KernelAccess.state(h.core());
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.WORKER, new FactionSlot(0), new Vec2d(20.5, 40.5), 1, 0));
        int worker = h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .findFirst()
                .orElseThrow();
        h.submit(
                ConstructionTest.P0,
                new PlaceBuilding(ConstructionTest.HOUSE, 40, 40, 0, UnitSet.of(), QueueMode.REPLACE));
        h.step();
        int house = s.buildings().handles()[1];
        h.submit(ConstructionTest.P0, Move.replace(UnitSet.of(worker), new Vec2d(26.5, 40.5)));
        h.submit(ConstructionTest.P0, new Build(UnitSet.of(worker), house, QueueMode.QUEUE));
        h.step();
        h.submit(ConstructionTest.P0, new CancelBuilding(house));
        int i = Handle.index(worker);
        assertThat(h.runUntil(() -> s.ai().state(i) == UnitState.IDLE, 400))
                .as("idle after the walk")
                .isTrue();
        assertThat(s.orders().size(i)).isZero();
        h.runTicks(20);
        assertThat(h.invariantViolations()).isEmpty();
    }
}
