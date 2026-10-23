package dev.strategos.core.client.hud;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** UI-02.1: at every tested window and GUI scale the parts stay inside the safe area and never overlap. */
class HudLayoutTest {
    private static final int[][] WINDOWS = {{1280, 720}, {1920, 1080}, {2560, 1440}, {3456, 2234}};

    @Test
    void partsInsideTheSafeAreaAndApart() {
        for (int[] window : WINDOWS) {
            for (int scale = 1; scale <= 4; scale++) {
                HudLayout layout = HudLayout.compute(window[0], window[1], scale);
                String at = window[0] + " x " + window[1] + " at " + scale;
                assertThat(layout.safe().inside(new HudRect(0, 0, layout.guiWidth(), layout.guiHeight())))
                        .as(at)
                        .isTrue();
                List<HudRect> parts = layout.parts();
                for (int i = 0; i < parts.size(); i++) {
                    HudRect a = parts.get(i);
                    assertThat(a.width()).as(at + " " + a).isPositive();
                    assertThat(a.height()).as(at + " " + a).isPositive();
                    assertThat(a.inside(layout.safe())).as(at + " " + a).isTrue();
                    for (int j = i + 1; j < parts.size(); j++) {
                        assertThat(a.overlaps(parts.get(j)))
                                .as(at + " " + a + " and " + parts.get(j))
                                .isFalse();
                    }
                }
                assertThat(layout.selectionPanel().width()).as(at).isGreaterThanOrEqualTo(100);
            }
        }
    }

    @Test
    void guiSizeRoundsUpAsTheWindowDoes() {
        HudLayout layout = HudLayout.compute(3456, 2234, 3);
        assertThat(layout.guiWidth()).isEqualTo(1152);
        assertThat(layout.guiHeight()).isEqualTo(745); // 744.67 up
    }

    @Test
    void theSelectionPanelIsCentredAndCapped() {
        HudLayout wide = HudLayout.compute(3456, 2234, 1);
        assertThat(wide.selectionPanel().width()).isEqualTo(HudLayout.SELECTION_MAX);
        int left = wide.selectionPanel().x() - wide.minimap().right();
        int right = wide.commandCard().x() - wide.selectionPanel().right();
        assertThat(Math.abs(left - right)).isLessThanOrEqualTo(1);
        assertThat(wide.groupBar().bottom())
                .isLessThanOrEqualTo(wide.selectionPanel().y());
    }

    @Test
    void tooltipsStayInsideTheSafeArea() {
        for (int[] window : WINDOWS) {
            for (int scale = 1; scale <= 4; scale++) {
                HudLayout layout = HudLayout.compute(window[0], window[1], scale);
                double[][] cursors = {
                    {0, 0},
                    {layout.guiWidth(), 0},
                    {0, layout.guiHeight()},
                    {layout.guiWidth(), layout.guiHeight()},
                    {layout.guiWidth() / 2.0, layout.guiHeight() / 2.0}
                };
                for (double[] c : cursors) {
                    HudRect tip = layout.tooltip(90, 20, c[0], c[1]);
                    assertThat(tip.inside(layout.safe()))
                            .as(window[0] + "@" + scale + " " + tip)
                            .isTrue();
                }
            }
        }
    }

    @Test
    void aTooltipSitsBelowRightOfTheCursorWhereThereIsRoom() {
        HudLayout layout = HudLayout.compute(1920, 1080, 2);
        assertThat(layout.tooltip(90, 20, 100, 100)).isEqualTo(new HudRect(108, 108, 90, 20));
    }
}
