package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CadenceTest {

    @Test
    void runsEveryPeriodFromItsOffset() {
        Cadence cadence = new Cadence(10, 3);
        List<Long> ticks = new ArrayList<>();
        for (long tick = 0; tick < 30; tick++) {
            if (cadence.runsAt(tick)) {
                ticks.add(tick);
            }
        }
        assertThat(ticks).containsExactly(3L, 13L, 23L);
    }

    @Test
    void everyTickRunsAlways() {
        for (long tick = -3; tick < 5; tick++) {
            assertThat(Cadence.EVERY_TICK.runsAt(tick)).isTrue();
        }
    }

    @Test
    void rejectsABadPeriodOrOffset() {
        assertThatThrownBy(() -> new Cadence(0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Cadence(10, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Cadence(10, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
