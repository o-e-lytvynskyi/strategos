package dev.strategos.core.client.hud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.BuildingState;
import java.util.List;
import org.junit.jupiter.api.Test;

/** CMB-10's capture bar (L1): the segments tile the bar by each faction's share, in faction order. */
class CaptureBarTest {
    private static BuildingState.Entry building(List<Integer> shares) {
        return new BuildingState.Entry(
                1, 2, 0, 3, 4, 0, 5, 2, 0, 255, false, List.of(), false, 0, 0, 0, -1, 0, false, List.of(), shares);
    }

    @Test
    void theSegmentsTileTheBarByShare() {
        HudRect panel = new HudRect(10, 20, 100, 60);
        HudRect bar = SelectionPanel.captureBar(panel, 4);
        assertThat(bar.x()).isEqualTo(14);
        assertThat(bar.width()).isEqualTo(92);

        List<int[]> segments = SelectionPanel.captureSegments(bar, building(List.of(153, 102)));

        assertThat(segments).hasSize(2);
        assertThat(segments.get(0)).containsExactly(0, 14, 55); // 60 % of 92
        assertThat(segments.get(1)).containsExactly(1, 69, 37);
        assertThat(segments.get(1)[1] + segments.get(1)[2]).isEqualTo(bar.right());
    }

    @Test
    void aFactionWithNoShareHasNoSegmentAndNoCaptureNone() {
        HudRect bar = SelectionPanel.captureBar(new HudRect(0, 0, 100, 60), 4);
        List<int[]> segments = SelectionPanel.captureSegments(bar, building(List.of(0, 0, 255)));
        assertThat(segments).hasSize(1);
        assertThat(segments.get(0)[0]).isEqualTo(2);
        assertThat(segments.get(0)[2]).isEqualTo(bar.width());
        assertThat(SelectionPanel.captureSegments(bar, building(List.of()))).isEmpty();
    }
}
