package dev.strategos.core.client.hud;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * UI-06.1: at every tested window and every GUI scale Minecraft allows there, the command card's 15 cells and the
 * selection panel's icon, lines and queue row stay inside their parts, apart, and at least an item icon large.
 */
class CommandCardLayoutTest {
    private static final int[][] WINDOWS = {
        {854, 480},
        {1024, 768},
        {1280, 720},
        {1280, 1024},
        {1366, 768},
        {1600, 900},
        {1920, 1080},
        {2560, 1080},
        {2560, 1440},
        {3440, 1440},
        {3456, 2234},
        {3840, 2160}
    };

    /** Minecraft's largest automatic GUI scale for a window: a GUI of at least 320 × 240 (Window#calculateScale). */
    static int maxScale(int width, int height) {
        int scale = 1;
        while (scale < width && scale < height && width / (scale + 1) >= 320 && height / (scale + 1) >= 240) {
            scale++;
        }
        return scale;
    }

    static List<HudLayout> layouts() {
        List<HudLayout> out = new ArrayList<>();
        for (int[] w : WINDOWS) {
            for (int scale = 1; scale <= maxScale(w[0], w[1]); scale++) {
                out.add(HudLayout.compute(w[0], w[1], scale));
            }
        }
        return out;
    }

    @Test
    void theCardsCellsFitApartAndLargeEnough() {
        int pad = HudTheme.DEFAULT.padding();
        for (HudLayout layout : layouts()) {
            HudRect card = layout.commandCard();
            String at = layout.guiWidth() + " x " + layout.guiHeight();
            for (int a = 0; a < CommandCard.SLOTS; a++) {
                HudRect cell = CommandCard.cell(card, pad, a);
                assertThat(cell.width()).as(at).isGreaterThanOrEqualTo(CommandCard.CELL_MIN);
                assertThat(cell.inside(card))
                        .as(at + " cell " + a + " " + cell + " in " + card)
                        .isTrue();
                for (int b = a + 1; b < CommandCard.SLOTS; b++) {
                    assertThat(cell.overlaps(CommandCard.cell(card, pad, b)))
                            .as(at + " cells " + a + ", " + b)
                            .isFalse();
                }
            }
        }
    }

    @Test
    void thePanelsPartsFitApart() {
        int pad = HudTheme.DEFAULT.padding();
        for (HudLayout layout : layouts()) {
            HudRect panel = layout.selectionPanel();
            String at = layout.guiWidth() + " x " + layout.guiHeight() + " " + panel;
            HudRect icon = SelectionPanel.icon(panel, pad);
            assertThat(icon.inside(panel)).as(at).isTrue();
            List<HudRect> lines = new ArrayList<>();
            for (int n = 0; n < 3; n++) {
                int[] xy = SelectionPanel.line(panel, pad, n);
                HudRect line = new HudRect(xy[0], xy[1], panel.right() - pad - xy[0], SelectionPanel.LINE - 1);
                assertThat(line.inside(panel)).as(at + " line " + n).isTrue();
                assertThat(line.width()).as(at + " line " + n).isGreaterThanOrEqualTo(80);
                assertThat(line.overlaps(icon)).as(at + " line " + n).isFalse();
                lines.add(line);
            }
            for (int a = 0; a < lines.size(); a++) {
                for (int b = a + 1; b < lines.size(); b++) {
                    assertThat(lines.get(a).overlaps(lines.get(b)))
                            .as(at + " lines " + a + ", " + b)
                            .isFalse();
                }
            }
            for (int k = 0; k < 5; k++) {
                HudRect cell = SelectionPanel.queueCell(panel, pad, k);
                assertThat(cell.inside(panel)).as(at + " queue " + k).isTrue();
                assertThat(cell.overlaps(icon) || cell.overlaps(lines.get(0)) || cell.overlaps(lines.get(1)))
                        .as(at + " queue " + k + " under the icon's lines")
                        .isFalse();
                if (k > 0) {
                    assertThat(cell.overlaps(SelectionPanel.queueCell(panel, pad, k - 1)))
                            .isFalse();
                }
            }
            int capacity = SelectionPanel.typeCapacity(panel, pad);
            assertThat(capacity).as(at + " types").isGreaterThanOrEqualTo(10);
            for (int k = 0; k < capacity; k++) {
                assertThat(SelectionPanel.typeCell(panel, pad, k).inside(panel))
                        .as(at + " type " + k)
                        .isTrue();
            }
        }
    }

    @Test
    void minecraftsScalesAreTheOnesTested() {
        assertThat(maxScale(854, 480)).isEqualTo(2);
        assertThat(maxScale(1920, 1080)).isEqualTo(4);
        assertThat(maxScale(3840, 2160)).isEqualTo(9);
        assertThat(layouts()).hasSize(56);
    }
}
