package dev.strategos.core.sim.build;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.Build;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * M4 §1.6 row 1: workers sent to an own foundation take a build order on it and finish it; another faction's, a
 * finished one and an unknown handle are refused for every unit.
 */
class BuildCommandTest {
    @Test
    void workersSentToAFoundationFinishIt() {
        SimHarness h = ConstructionTest.match();
        MatchState s = KernelAccess.state(h.core());
        int[] workers = ConstructionTest.buildersOnSlots(h, 2);
        h.submit(
                ConstructionTest.P0,
                new PlaceBuilding(ConstructionTest.HOUSE, 30, 30, 0, UnitSet.of(), QueueMode.REPLACE));
        h.step();
        int house = house(s);
        assertThat(s.buildings().state(house))
                .as("a foundation with no builder")
                .isEqualTo(BuildingStore.FOUNDATION);
        h.submit(ConstructionTest.P0, Build.replace(UnitSet.of(workers), house));
        CommandResult r = result(h);
        assertThat(r.outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(r.accepted()).isEqualTo(2);
        for (int t = 0; t < 30 * 20 && s.buildings().state(house) != BuildingStore.COMPLETE; t++) {
            h.step();
        }
        assertThat(s.buildings().state(house))
                .as("the house, finished by the workers sent")
                .isEqualTo(BuildingStore.COMPLETE);
    }

    @Test
    void anotherFactionsAFinishedAndAnUnknownBuildingAreRefused() {
        SimHarness h = ConstructionTest.match();
        MatchState s = KernelAccess.state(h.core());
        int[] workers = ConstructionTest.buildersOnSlots(h, 1);
        int centre = s.buildings().handles()[0];
        h.submit(ConstructionTest.P0, Build.replace(UnitSet.of(workers), centre));
        assertThat(result(h).firstReason()).as("a finished centre").isEqualTo(RejectReason.UNSUPPORTED);
        h.submit(new PlayerSlot(1), Build.replace(UnitSet.of(workers), centre));
        assertThat(result(h).firstReason())
                .as("faction 1 at faction 0's centre")
                .isEqualTo(RejectReason.NOT_OWNER);
        h.submit(ConstructionTest.P0, Build.replace(UnitSet.of(workers), 12_345));
        assertThat(result(h).firstReason()).isEqualTo(RejectReason.UNKNOWN_TARGET);
    }

    private static int house(MatchState s) {
        for (int handle : s.buildings().handles()) {
            if (s.buildings().state(handle) != BuildingStore.COMPLETE) {
                return handle;
            }
        }
        throw new AssertionError("no foundation");
    }

    private static CommandResult result(SimHarness h) {
        List<CommandResult> results = h.step().events().stream()
                .filter(CommandResult.class::isInstance)
                .map(CommandResult.class::cast)
                .filter(r -> r.kind().equals("strategos:build"))
                .toList();
        assertThat(results).hasSize(1);
        return results.get(0);
    }
}
