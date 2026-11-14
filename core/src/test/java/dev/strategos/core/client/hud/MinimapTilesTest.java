package dev.strategos.core.client.hud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.MatchNews;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** UI-20.2 and the pings: live construction rebuilds a tile within a second and at most once a second. */
class MinimapTilesTest {
    private static BuildingState.Entry building(int handle, int x, int z, int progress) {
        return new BuildingState.Entry(
                handle, 0, 0, x, z, 0, 0, 1, progress, 0, true, List.of(), false, 0, 0, 0, 0, 0, false, List.of());
    }

    @Test
    void aChangedChunkWithinASecondAtMostOnce() {
        DirtyTiles tiles = new DirtyTiles(8, 8);
        tiles.observe(List.of(building(1, 40, 40, 0))); // first sight: a new foundation marks its 3 x 3 chunks
        assertThat(tiles.pending()).isEqualTo(9);
        tiles.drain(-100, 100, tile -> {});
        assertThat(tiles.pending()).isZero();
        List<Long> builds = new ArrayList<>();
        long changedAt = 5;
        for (long t = 0; t < 200; t++) {
            if (t >= changedAt && t < changedAt + 100) {
                tiles.observe(List.of(building(1, 40, 40, (int) t))); // progress moves every tick for 5 s
            }
            long now = t;
            tiles.drain(now, 100, tile -> {
                if (tile == 2 * 8 + 2) { // the building's own chunk
                    builds.add(now);
                }
            });
        }
        assertThat(builds.get(0)).as("rebuilt at once, well within 1 s").isEqualTo(changedAt);
        for (int i = 1; i < builds.size(); i++) {
            assertThat(builds.get(i) - builds.get(i - 1))
                    .as("at most once a second")
                    .isGreaterThanOrEqualTo(20);
        }
        assertThat(builds.get(builds.size() - 1) - (changedAt + 99))
                .as("the last change is rebuilt within a second")
                .isLessThanOrEqualTo(20);
        assertThat(tiles.pending()).isZero();
    }

    @Test
    void aTileBuiltByTheRoundWaitsItsSecond() {
        DirtyTiles tiles = new DirtyTiles(4, 4);
        tiles.built(5, 100);
        tiles.mark(5);
        List<Integer> got = new ArrayList<>();
        tiles.drain(110, 4, got::add);
        assertThat(got).isEmpty();
        tiles.drain(120, 4, got::add);
        assertThat(got).containsExactly(5);
    }

    @Test
    void theBudgetBoundsATickAndTheEdgeIsClipped() {
        DirtyTiles tiles = new DirtyTiles(2, 2);
        tiles.markAround(0, 0); // only four tiles exist
        List<Integer> got = new ArrayList<>();
        tiles.drain(0, 3, got::add);
        assertThat(got).hasSize(3);
        assertThat(tiles.pending()).isEqualTo(1);
    }

    @Test
    void anAlertAndAMusterRingForThreeSeconds() {
        MinimapPings pings = new MinimapPings();
        MatchNews alert = new MatchNews(1, 0, MatchNews.ALERT, 0, 0, 12.4f, 30.6f);
        pings.observe(1, alert, 0, null, 100);
        assertThat(pings.active(100)).hasSize(1);
        assertThat(pings.active(100).get(0).x()).isEqualTo(12);
        assertThat(pings.active(100).get(0).z()).isEqualTo(31);
        pings.observe(1, alert, 0, null, 120); // the same alert is not pinged again
        assertThat(pings.active(120)).hasSize(1);
        assertThat(pings.active(159)).hasSize(1);
        assertThat(pings.active(160)).isEmpty();
        pings.observe(1, alert, 1, alert, 160);
        assertThat(pings.active(160)).hasSize(1);
    }

    @Test
    void aBuildingThatAppearsOrGoesAwayMarksItsTiles() {
        DirtyTiles tiles = new DirtyTiles(8, 8);
        tiles.observe(List.of(building(1, 40, 40, 0), building(2, 100, 100, 0)));
        tiles.drain(0, 100, tile -> {});
        tiles.observe(List.of(building(1, 40, 40, 0), building(2, 100, 100, 0)));
        assertThat(tiles.pending()).as("nothing moved").isZero();
        tiles.observe(List.of(building(2, 100, 100, 0))); // building 1 destroyed
        List<Integer> rebuilt = new ArrayList<>();
        tiles.drain(40, 100, rebuilt::add);
        assertThat(rebuilt)
                .as("the ruin's chunk and the eight round it")
                .hasSize(9)
                .contains(2 * 8 + 2);
        tiles.observe(List.of(building(2, 100, 100, 0)));
        assertThat(tiles.pending()).as("a building gone is marked once").isZero();
    }
}
