package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.StartSlot;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A player that holds the fallen enemy's start with a second civic centre (a match's mirror at easy, past minute 45:
 * M10 T26's matrix) still has its base at its own start. Taking the centre it stands on first made the base, and so the
 * front a wave marches to, the middle of that building: forty soldiers attack-moved onto a footprint they could not
 * enter, crowded its face and stood 640 ticks without progress.
 */
class BaseTest {
    private static final TemplateId CENTRE = new TemplateId("strategos:building/civic_centre");

    @Test
    void theBaseStaysAtTheOwnStartWhenACentreStandsAtTheEnemys() {
        SimHarness h = SimHarness.builder()
                .size(160, 160)
                .yBand(0, 8, 1)
                .factions(2)
                .starts(List.of(new StartSlot(20, 8, 20, 0), new StartSlot(120, 8, 120, 0)))
                .rules(MatchRules.DEFAULTS.withPlay(HeadlessMatch.EXPLORED))
                .build();
        h.publishAll(VoxelFixtures.flat(160, 160, 8, 1));
        h.step();
        // the captured centre is spawned first, so it is the one a search in the buildings' order finds first
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, new FactionSlot(0), 115, 115, 0));
        h.step();
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, new FactionSlot(0), 15, 15, 0));
        h.step();
        h.step();

        Base base = Base.of(new AiView(h.core().queries(), 0, new AiBudget(AiBudget.CAP)));

        assertThat(base).isNotNull();
        assertThat(base.x()).as("the base is the centre at its own start").isLessThan(60);
        assertThat(base.z()).as("and on its own side").isLessThan(60);
        assertThat(base.aimX()).as("what it aims at is the enemy's start").isGreaterThan(100);
        assertThat(base.aimZ()).isGreaterThan(100);
    }
}
