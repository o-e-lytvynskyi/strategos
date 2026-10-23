package dev.strategos.core.client.hud;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** CMB-06: bars over the selected and the hurt, none over a whole unit not selected; green to red. */
class HealthBarsTest {
    @Test
    void selectedOrHurtUnitsGetABarOverTheirHead() {
        List<HealthBars.Bar> bars = HealthBars.extract(List.of(
                new HealthBars.Unit(1, 64, 1, 1.8, 0.6, false, 255),
                new HealthBars.Unit(2, 64, 2, 1.8, 0.6, true, 255),
                new HealthBars.Unit(3, 64, 3, 1.8, 0.6, false, 51)));
        assertThat(bars).hasSize(2);
        assertThat(bars.get(0).x()).isEqualTo(2);
        assertThat(bars.get(0).y()).isEqualTo(64 + 1.8 + HealthBars.ABOVE);
        assertThat(bars.get(0).fill()).isEqualTo(1);
        assertThat(bars.get(1).fill()).isEqualTo(0.2);
        assertThat(bars.get(1).width()).isEqualTo(HealthBars.MIN_WIDTH);
    }

    @Test
    void theColourRunsFromGreenThroughYellowToRed() {
        assertThat(HealthBars.colour(1)).isEqualTo(0xFF00FF30);
        assertThat(HealthBars.colour(0.5)).isEqualTo(0xFFFFFF30);
        assertThat(HealthBars.colour(0)).isEqualTo(0xFFFF0030);
    }
}
