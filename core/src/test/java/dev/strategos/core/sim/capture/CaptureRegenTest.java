package dev.strategos.core.sim.capture;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Garrison;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import org.junit.jupiter.api.Test;

/**
 * BLD-10 acceptance 3 and CMB-10's capture in a match (L1, M9 spec §2.6–2.7): each garrisoned unit adds its capture
 * strength to the building's regeneration; enough strength takes the building, which then trains for its new owner.
 */
class CaptureRegenTest {
    static final TemplateId CENTRE = new TemplateId("strategos:building/civic_centre");

    final SimHarness h =
            SimHarness.builder().size(128, 128).yBand(0, 31, 1).factions(2).build();
    final MatchState s;
    final int centre;

    CaptureRegenTest() {
        h.publishAll(VoxelFixtures.flat(128, 128, 32, 1));
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, new FactionSlot(0), 40, 40, 0));
        h.step();
        s = KernelAccess.state(h.core());
        centre = s.buildings().handles()[0];
    }

    @Test
    void fiveInfantryInsideAddTwelveAndAHalfASecond() {
        assertThat(CaptureSystem.regen(s, centre, 0)).as("the centre's own 30").isEqualTo(30_000);
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, new FactionSlot(0), new Vec2d(45, 56), 5, 1));
        int[] infantry = h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .toArray();
        h.submit(new PlayerSlot(0), new Garrison(UnitSet.of(infantry), centre));
        h.runTicks(200);
        assertThat(s.garrison().count(centre)).isEqualTo(5);
        assertThat(CaptureSystem.regen(s, centre, 0)).isEqualTo(30_000 + 5 * 2_500);
    }

    @Test
    void strengthAboveRegenerationTakesTheCentreOnlyAtZeroOwnerPoints() {
        long perSecond = 20 * 2_500; // twenty infantry
        int took = -1;
        for (int t = 0; t < 120 * CaptureSystem.SECOND && took < 0; t++) {
            s.play().capture().contribute(centre, 1, perSecond);
            h.step();
            long[] p = s.play().capture().points(centre);
            if (s.buildings().faction(centre) == 1) {
                took = t;
            } else if (p != null) {
                assertThat(p[0]).as("still the owner's while it holds points").isPositive();
            }
        }
        assertThat(took / CaptureSystem.SECOND).as("seconds").isBetween(97, 101);
        assertThat(s.play().capture().points(centre))
                .as("the new owner holds every point")
                .isNull();
    }
}
