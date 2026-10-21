package dev.strategos.mod.server.tick;

import static org.assertj.core.api.Assertions.assertThat;

import net.minecraft.world.TickRateManager;
import org.junit.jupiter.api.Test;

/** CORE-02.6: the matches step only when the game runs; /tick freeze stops them and /tick step runs exact steps. */
class TickGateTest {

    @Test
    void aRunningGameSteps() {
        assertThat(TickDriver.shouldStep(new TickRateManager())).isTrue();
    }

    @Test
    void aFrozenGameDoesNotStep() {
        TickRateManager rates = new TickRateManager();
        rates.setFrozen(true);
        rates.tick();

        assertThat(TickDriver.shouldStep(rates)).isFalse();
    }

    @Test
    void stepFiveRunsExactlyFiveSteps() {
        TickRateManager rates = new TickRateManager();
        rates.setFrozen(true);
        rates.setFrozenTicksToRun(5);

        int steps = 0;
        for (int i = 0; i < 6; i++) {
            rates.tick();
            if (TickDriver.shouldStep(rates)) {
                steps++;
            }
        }

        assertThat(steps).isEqualTo(5);
    }
}
