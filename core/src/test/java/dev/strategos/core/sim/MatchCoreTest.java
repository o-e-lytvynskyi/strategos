package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

class MatchCoreTest {

    @Test
    void startsAtTickZeroWithNoUnits() {
        MatchCore core = SimHarness.builder().build().core();
        assertThat(core.queries().tick()).isZero();
        assertThat(core.queries().unitCount()).isZero();
        assertThat(core.queries().unitHandles()).isEmpty();
    }

    @Test
    void aStepForTheWrongTickThrows() {
        MatchCore core = SimHarness.builder().build().core();
        assertThatThrownBy(() -> core.step(TickInputs.empty(2))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> core.step(TickInputs.empty(0))).isInstanceOf(IllegalArgumentException.class);
        assertThat(core.queries().tick()).isZero();
    }

    @Test
    void emptyStepsAdvanceTheTick() {
        MatchCore core = SimHarness.builder().build().core();
        for (long tick = 1; tick <= 5; tick++) {
            TickOutputs out = core.step(TickInputs.empty(tick));
            assertThat(out.tick()).isEqualTo(tick);
            assertThat(out.events()).isEmpty();
            assertThat(out.bodies().count()).isZero();
        }
        assertThat(core.queries().tick()).isEqualTo(5);
    }

    @Test
    void timesEveryPhaseOfEveryStep() {
        MatchCore core = SimHarness.builder().build().core();
        for (long tick = 1; tick <= 3; tick++) {
            core.step(TickInputs.empty(tick));
        }
        assertThat(core.timers().stats()).hasSize(Phase.values().length + 3);
        assertThat(core.timers().stats().get(0).samples()).isEqualTo(3);
    }
}
