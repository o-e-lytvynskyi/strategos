package dev.strategos.core.sim.capture;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Garrison;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.Train;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.build.BuildingLife;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import org.junit.jupiter.api.Test;

/**
 * M9 spec §2.5: a building changing owner — the units inside come out, the old owner's queue is refunded and its
 * housing lost, the rally point cleared; the new owner houses it, and the territory follows.
 */
class TransferTest {
    static final TemplateId CENTRE = new TemplateId("strategos:building/civic_centre");
    static final int FOOD = 0;

    @Test
    void aCentreChangingHandsRefundsEjectsAndRehouses() {
        SimHarness h =
                SimHarness.builder().size(128, 128).yBand(0, 31, 1).factions(2).build();
        h.publishAll(VoxelFixtures.flat(128, 128, 32, 1));
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, new FactionSlot(0), 40, 40, 0));
        h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "food", 1_000));
        h.step();
        MatchState s = KernelAccess.state(h.core());
        int centre = s.buildings().handles()[0];
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.WORKER, new FactionSlot(0), new Vec2d(45, 56), 2, 1));
        int[] workers = h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .toArray();
        h.submit(new PlayerSlot(0), new Garrison(UnitSet.of(workers), centre));
        h.runTicks(200);
        assertThat(s.garrison().count(centre)).as("both inside").isEqualTo(2);
        h.submit(new PlayerSlot(0), new Train(centre, TemplateSet.WORKER));
        h.step();
        long fed = s.treasury().stock(0, FOOD);
        assertThat(s.buildings().queues().queueSize(centre)).isEqualTo(1);
        int housed0 = s.population().housing(0);

        BuildingLife.transfer(s, centre, 1);

        assertThat(s.buildings().faction(centre)).isEqualTo(1);
        assertThat(s.garrison().count(centre)).as("ejected").isZero();
        assertThat(s.buildings().queues().queueSize(centre)).isZero();
        assertThat(s.treasury().stock(0, FOOD))
                .as("the queue refunded to the old owner")
                .isGreaterThan(fed);
        assertThat(s.population().housing(0)).isLessThan(housed0);
        assertThat(s.population().housing(1)).isEqualTo(housed0 - s.population().housing(0));
        h.runTicks(25);
        assertThat(s.territorySystem().grid().ownerAt(45, 45))
                .as("its land follows")
                .isEqualTo(1);
    }
}
