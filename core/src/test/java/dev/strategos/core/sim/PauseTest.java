package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

/**
 * MATCH-05.1–2 in L1: a pause is steps not taken (the runtime's {@code PAUSED} state), so a run paused at tick 80 for
 * however long, with an order given meanwhile, reaches tick 200 at the same hash as one that gave the order at tick 80
 * and never paused; the order applies at the first tick after the resume.
 */
class PauseTest {
    static SimHarness match() {
        SimHarness h = SimHarness.builder().build();
        h.spawn(new FactionSlot(0), new Vec2d(8, 8), 6, 2.0);
        return h;
    }

    @Test
    void aPausedRunReachesTheSameHash() {
        SimHarness steady = match();
        SimHarness paused = match();
        steady.runTicks(79);
        paused.runTicks(79);
        int[] units = steady.core().queries().unitHandles();
        steady.submit(new PlayerSlot(0), Move.replace(UnitSet.of(units), new Vec2d(24, 20)));
        // paused: no step for as long as the player thinks; the order waits in the inbox
        paused.submit(new PlayerSlot(0), Move.replace(UnitSet.of(units), new Vec2d(24, 20)));
        while (steady.tick() < 200) {
            steady.step();
            paused.step();
        }
        assertThat(paused.stateHash()).isEqualTo(steady.stateHash());
    }

    @Test
    void ordersGivenInPauseApplyOnResume() {
        SimHarness h = match();
        h.runTicks(79);
        long pausedAt = h.tick();
        int[] units = h.core().queries().unitHandles();
        h.submit(new PlayerSlot(0), Move.replace(UnitSet.of(units), new Vec2d(24, 20)));
        assertThat(h.tick()).as("nothing steps while paused").isEqualTo(pausedAt);
        TickOutputs resumed = h.step();
        assertThat(resumed.tick()).isEqualTo(pausedAt + 1);
        assertThat(resumed.events().stream()
                        .filter(CommandResult.class::isInstance)
                        .map(CommandResult.class::cast)
                        .filter(r -> r.accepted() == units.length))
                .as("the order applies in the first step after the resume")
                .hasSize(1);
        assertThat(h.core().queries().unit(units[0]).state()).isEqualTo(UnitState.WALKING);
    }
}
