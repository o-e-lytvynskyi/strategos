package dev.strategos.core.sim.rank;

import static dev.strategos.core.sim.rank.RankFixtures.F0;
import static dev.strategos.core.sim.rank.RankFixtures.F1;
import static dev.strategos.core.sim.rank.RankFixtures.spawn;
import static dev.strategos.core.sim.rank.RankFixtures.spearman;
import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.client.hud.SelectionPanel;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.UnitDetail;
import dev.strategos.core.protocol.WireCodec;
import dev.strategos.core.sim.replicate.ReplicationService;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CMB-11.4: a unit's rank and what it has earned toward the next ride {@code unit_detail} to its owner as a fourth
 * trailer, the unit panel reads them, and a frame without any is byte-for-byte what it was.
 */
class RankReplicationTest {
    private static final int MATCH = 1;

    private static UnitDetail.Unit plain(int handle, UnitDetail.Standing standing) {
        return new UnitDetail.Unit(
                handle,
                0,
                2,
                100f,
                100f,
                0.2f,
                List.of(),
                -1,
                0,
                UnitDetail.NO_TARGET,
                UnitDetail.NO_STANCE,
                List.of(),
                standing);
    }

    private static UnitDetail.Standing standing(SimHarness h, int unit) {
        return ReplicationService.detail(h.queries(), MATCH, F0, List.of(unit))
                .units()
                .get(0)
                .standing();
    }

    @Test
    void theDetailCarriesTheRankTheExperienceAndTheNextThreshold() {
        SimHarness h = RankFixtures.match();
        int a = spearman(h, F0, 10, 16);
        assertThat(standing(h, a)).as("nothing earned").isEqualTo(UnitDetail.Standing.NONE);
        int worker = spawn(h, TemplateSet.WORKER, F1, 11, 16);
        Ranks.credit(RankFixtures.state(h), a, worker, 25, 50); // 30
        assertThat(standing(h, a)).isEqualTo(new UnitDetail.Standing(0, 30_000, 200_000));
        Ranks.credit(RankFixtures.state(h), a, worker, 50, 50); // +60 x 1, then 60 more
        Ranks.credit(RankFixtures.state(h), a, worker, 50, 50);
        Ranks.credit(RankFixtures.state(h), a, worker, 50, 50);
        assertThat(standing(h, a))
                .as("210: rank 1, 500 for the next")
                .isEqualTo(new UnitDetail.Standing(1, 210_000, 500_000));
        for (int k = 0; k < 15; k++) {
            Ranks.credit(RankFixtures.state(h), a, worker, 50, 50);
        }
        assertThat(standing(h, a).rank()).isEqualTo(3);
        assertThat(standing(h, a).next()).as("the top rank has no next").isZero();
    }

    @Test
    void theTrailerRoundTripsAndAFrameWithoutOneIsUnchanged() {
        UnitDetail none = new UnitDetail(MATCH, 9, List.of(plain(5, UnitDetail.Standing.NONE)));
        byte[] noneBytes = WireCodec.encode(UnitDetail.WIRE, none);
        assertThat(WireCodec.encode(
                        UnitDetail.WIRE,
                        new UnitDetail(
                                MATCH, 9, List.of(new UnitDetail.Unit(5, 0, 2, 100f, 100f, 0.2f, List.of(), -1, 0)))))
                .as("no rank, no extra byte")
                .isEqualTo(noneBytes);

        UnitDetail one = new UnitDetail(MATCH, 9, List.of(plain(5, new UnitDetail.Standing(2, 340_000, 500_000))));
        byte[] oneBytes = WireCodec.encode(UnitDetail.WIRE, one);
        assertThat(WireCodec.decode(UnitDetail.WIRE, oneBytes)).isEqualTo(one);
        assertThat(oneBytes)
                .as(
                        "three empty counts (attacks, stances, cooldowns), the count of one, then place, rank, 340000 and 500000"
                                + " as varlongs (3 bytes each)")
                .hasSize(noneBytes.length + 3 + 1 + 1 + 1 + 3 + 3);

        UnitDetail mixed = new UnitDetail(
                MATCH,
                9,
                List.of(
                        plain(5, UnitDetail.Standing.NONE),
                        plain(6, new UnitDetail.Standing(0, 1, 200_000)),
                        plain(7, new UnitDetail.Standing(3, 1_020_000, 0))));
        byte[] mixedBytes = WireCodec.encode(UnitDetail.WIRE, mixed);
        assertThat(WireCodec.decode(UnitDetail.WIRE, mixedBytes)).isEqualTo(mixed);
        assertThat(mixedBytes.length).isLessThanOrEqualTo(UnitDetail.WIRE.maxSize());
    }

    @Test
    void thePanelShowsTheRankAndTheProgressToTheNext() {
        assertThat(SelectionPanel.rankLine(UnitDetail.Standing.NONE)).isEmpty();
        assertThat(SelectionPanel.rankLine(new UnitDetail.Standing(0, 30_000, 200_000)))
                .isEqualTo("Rank 0 · 30 / 200 XP");
        assertThat(SelectionPanel.rankLine(new UnitDetail.Standing(2, 540_000, 1_000_000)))
                .isEqualTo("Rank 2 · 540 / 1000 XP");
        assertThat(SelectionPanel.rankLine(new UnitDetail.Standing(3, 1_020_000, 0)))
                .as("at the top there is nothing to progress to")
                .isEqualTo("Rank 3 · 1020 XP");
    }
}
