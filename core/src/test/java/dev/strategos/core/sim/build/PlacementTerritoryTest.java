package dev.strategos.core.sim.build;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.prod.Techs;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import org.junit.jupiter.api.Test;

/**
 * TERR-02 acceptance 1 and 2 (L1, M9 spec §2.2): buildings go only on their faction's land, refused with the reason;
 * a second civic centre may go on neutral land from Town on, never on an enemy's.
 */
class PlacementTerritoryTest {
    static final TemplateId CENTRE = new TemplateId("strategos:building/civic_centre");
    static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    static final TechId TOWN = new TechId("strategos:phase_town");

    final SimHarness h =
            SimHarness.builder().size(320, 160).yBand(0, 31, 1).factions(2).build();
    final MatchState s;

    PlacementTerritoryTest() {
        h.publishAll(VoxelFixtures.flat(320, 160, 32, 1));
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, new FactionSlot(0), 40, 70, 0)); // centre (45.5, 75.5)
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, new FactionSlot(1), 260, 70, 0));
        for (String r : new String[] {"food", "wood", "stone", "iron"}) {
            h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), r, 5_000));
        }
        h.step();
        h.step();
        s = KernelAccess.state(h.core());
    }

    CommandResult place(TemplateId template, int x, int z) {
        h.submit(new PlayerSlot(0), new PlaceBuilding(template, x, z, 0, UnitSet.of(), QueueMode.REPLACE));
        h.step();
        return h.results().get(h.results().size() - 1);
    }

    @Test
    void aHouseGoesInsideItsLandAndIsRefusedOutsideWithTheReason() {
        assertThat(place(HOUSE, 60, 72).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        CommandResult outside = place(HOUSE, 120, 72);
        assertThat(outside.outcome()).isEqualTo(CommandOutcome.REJECTED);
        assertThat(outside.firstReason()).isEqualTo(RejectReason.OUTSIDE_TERRITORY);
        CommandResult enemy = place(HOUSE, 262, 90);
        assertThat(enemy.firstReason()).as("on the enemy's land").isEqualTo(RejectReason.OUTSIDE_TERRITORY);
    }

    @Test
    void aSecondCentreGoesOnNeutralLandFromTownOnAndNeverOnAnEnemys() {
        CommandResult village = place(CENTRE, 150, 70);
        assertThat(village.firstReason()).as("one centre in Village").isEqualTo(RejectReason.LIMIT);
        s.progress().complete(0, TOWN);
        Techs.reapply(s, null);
        CommandResult enemy = place(CENTRE, 230, 100);
        assertThat(enemy.firstReason()).isEqualTo(RejectReason.OUTSIDE_TERRITORY);
        assertThat(place(CENTRE, 150, 70).outcome()).as("neutral, in Town").isEqualTo(CommandOutcome.ACCEPTED);
    }
}
