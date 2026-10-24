package dev.strategos.core.client.hud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** UI-11.2: at every window and scale, the minimap maps a column to the pixel that shows it and a click back to it. */
class MinimapMappingTest {
    @Test
    void theMappingIsExactBothWays() {
        for (HudLayout layout : CommandCardLayoutTest.layouts()) {
            for (int[] space : new int[][] {{384, 384}, {256, 128}, {96, 200}}) {
                Minimap m = new Minimap(layout.minimap(), space[0], space[1]);
                for (int[] c : new int[][] {{0, 0}, {space[0] - 1, space[1] - 1}, {space[0] / 2, space[1] / 3}}) {
                    double[] p = m.toMap(c[0] + 0.5, c[1] + 0.5);
                    assertThat(m.contains(p[0], p[1])).isTrue();
                    double[] back = m.toWorld(p[0], p[1]);
                    assertThat(back[0]).isCloseTo(c[0] + 0.5, within(1e-9));
                    assertThat(back[1]).isCloseTo(c[1] + 0.5, within(1e-9));
                }
                double[] o = m.origin();
                double s = m.scale();
                assertThat(o[0]).isGreaterThanOrEqualTo(layout.minimap().x());
                assertThat(o[1]).isGreaterThanOrEqualTo(layout.minimap().y());
                assertThat(o[0] + space[0] * s)
                        .isLessThanOrEqualTo(layout.minimap().right() + 1e-9);
                assertThat(o[1] + space[1] * s)
                        .isLessThanOrEqualTo(layout.minimap().bottom() + 1e-9);
                assertThat(Math.max(space[0], space[1]) * s)
                        .as("the longer side fills the square")
                        .isCloseTo(layout.minimap().width(), within(1e-9));
                assertThat(m.contains(o[0] - 0.01, o[1])).isFalse();
            }
        }
    }
}
