package dev.strategos.core.sim.garrison;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.SetRally;
import dev.strategos.core.protocol.Train;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.testkit.FakeAllies;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** BLD-10.4 in L1: units trained at a building whose rally point is a holder of the owner or an ally garrison in it. */
class GarrisonRallyTest {
    final GarrisonScene g = new GarrisonScene();

    /** Trains one worker at {@code source}, rallied into {@code target}; its handle. */
    private int trainRallied(int source, int target, int faction) {
        var at = g.s.footprintOf(target);
        g.h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(faction), "food", 1_000));
        g.h.submit(new PlayerSlot(faction), new SetRally(UnitSet.of(source), at.anchorX() + 1, 1, at.anchorZ() + 1));
        g.h.step();
        assertThat(g.s.buildings().queues().rallyKind(source)).isEqualTo(BuildingStore.RALLY_BUILDING);
        g.h.submit(new PlayerSlot(faction), new Train(source, TemplateSet.WORKER));
        List<Integer> made = new ArrayList<>();
        for (int t = 0; t < 900 && made.isEmpty(); t++) {
            g.h.step().events().stream()
                    .filter(UnitCreated.class::isInstance)
                    .forEach(e -> made.add(((UnitCreated) e).handle()));
        }
        assertThat(made).hasSize(1);
        return made.get(0);
    }

    @Test
    void aUnitTrainedForAnOwnBuildingWithRoomGarrisonsOnArrival() {
        int source = g.building(0, 10, 10);
        int target = g.building(0, 60, 30);
        int unit = trainRallied(source, target, 0);
        assertThat(g.h.runUntil(() -> g.inside(unit), 900))
                .as("it walks over and goes in")
                .isTrue();
        assertThat(g.s.garrison().buildingOf(dev.strategos.core.shared.Handle.index(unit)))
                .isEqualTo(target);
    }

    @Test
    void anEnemysBuildingIsOnlyWalkedTo() {
        int source = g.building(0, 10, 10);
        int target = g.building(1, 60, 30);
        int unit = trainRallied(source, target, 0);
        g.h.runTicks(900);
        assertThat(g.inside(unit)).isFalse();
        assertThat(g.s.garrison().empty()).isTrue();
    }

    @Test
    void anAlliesBuildingTakesTheUnitIn() {
        // AFTER-M7: MATCH-06's teams replace FakeAllies in the allied GameTest.
        g.s.setAllies(FakeAllies.twoAgainstRest(3));
        int source = g.building(0, 10, 10);
        int target = g.building(1, 60, 30);
        int unit = trainRallied(source, target, 0);
        assertThat(g.h.runUntil(() -> g.inside(unit), 900)).isTrue();
        assertThat(g.s.garrison().count(target)).isEqualTo(1);
    }

    @Test
    void aFullBuildingIsOnlyWalkedTo() {
        int source = g.building(0, 10, 10);
        int target = g.building(0, 60, 30);
        g.garrisoned(TemplateSet.WORKER, 0, target, 20);
        int unit = trainRallied(source, target, 0);
        g.h.runTicks(900);
        assertThat(g.inside(unit)).isFalse();
        assertThat(g.s.garrison().count(target)).isEqualTo(20);
    }
}
