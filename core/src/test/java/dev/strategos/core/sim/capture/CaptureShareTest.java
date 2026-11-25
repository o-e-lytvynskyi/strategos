package dev.strategos.core.sim.capture;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.replicate.ReplicationService;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import org.junit.jupiter.api.Test;

/** CMB-10's replication (L1): the building_state entry carries each faction's capture share while a capture is open. */
class CaptureShareTest {
    static final TemplateId BARRACKS = new TemplateId("strategos:building/barracks");

    @Test
    void anOpenCaptureShowsEachFactionsShareAndAClosedOneNone() {
        SimHarness h =
                SimHarness.builder().size(128, 128).yBand(0, 31, 1).factions(2).build();
        h.publishAll(VoxelFixtures.flat(128, 128, 32, 1));
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(BARRACKS, new FactionSlot(1), 60, 60, 0));
        h.step();
        MatchState s = KernelAccess.state(h.core());
        int barracks = s.buildings().handles()[0];

        assertThat(entry(h, barracks).captureShares()).as("no capture open").isEmpty();

        for (int t = 0; t < 5 * CaptureSystem.SECOND; t++) {
            s.play().capture().contribute(barracks, 0, 30_000); // 30 a second against the 10 regenerated: 20 a second
            h.step();
        }
        BuildingState.Entry e = entry(h, barracks);
        // about 100 of the 800 points moved: 87.5 % the owner's, 12.5 % the taker's, in 255ths
        assertThat(e.captureShare(1)).isBetween(215, 230);
        assertThat(e.captureShare(0)).isBetween(25, 40);
        assertThat(e.captureShare(0) + e.captureShare(1)).isBetween(254, 256);
        assertThat(e.faction()).isEqualTo(1);
        assertThat(entry(h, barracks).captureShares()).hasSize(2);

        BuildingState.Entry seenByTaker = ReplicationService.buildingState(h.queries(), 1, new FactionSlot(0))
                .buildings()
                .get(0);
        assertThat(seenByTaker.captureShares()).as("every viewer sees the bar").isEqualTo(e.captureShares());
    }

    private static BuildingState.Entry entry(SimHarness h, int handle) {
        return ReplicationService.buildingState(h.queries(), 1, new FactionSlot(1)).buildings().stream()
                .filter(b -> b.handle() == handle)
                .findFirst()
                .orElseThrow();
    }
}
