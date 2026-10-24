package dev.strategos.core.sim.build;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * BLD-08.2: a faction holds one civic centre until phases allow more, its {@code Limit/count}: a second is refused
 * with {@code LIMIT}, before any rule of the ground; another faction's first is not; a house has no limit.
 */
class BuildingLimitTest {
    @Test
    void oneCivicCentreAFaction() {
        SimHarness h = ConstructionTest.match();
        h.submit(PlayerSlot.ADMIN, new dev.strategos.core.protocol.Grant(new FactionSlot(0), "stone", 1_000));
        h.submit(PlayerSlot.ADMIN, new dev.strategos.core.protocol.Grant(new FactionSlot(0), "iron", 1_000));
        h.submit(PlayerSlot.ADMIN, new dev.strategos.core.protocol.Grant(new FactionSlot(1), "wood", 1_000));
        h.submit(PlayerSlot.ADMIN, new dev.strategos.core.protocol.Grant(new FactionSlot(1), "stone", 1_000));
        h.submit(PlayerSlot.ADMIN, new dev.strategos.core.protocol.Grant(new FactionSlot(1), "iron", 1_000));
        h.step();
        h.submit(ConstructionTest.P0, place(ConstructionTest.CIVIC_CENTRE, 40, 40));
        CommandResult second = result(h);
        assertThat(second.outcome()).isEqualTo(CommandOutcome.REJECTED);
        assertThat(second.firstReason()).as("a second centre").isEqualTo(RejectReason.LIMIT);
        h.submit(new PlayerSlot(1), place(ConstructionTest.CIVIC_CENTRE, 53, 53)); // 72 from the first: spaced
        assertThat(result(h).outcome()).as("faction 1's first").isEqualTo(CommandOutcome.ACCEPTED);
        h.submit(ConstructionTest.P0, place(ConstructionTest.HOUSE, 20, 20));
        h.submit(ConstructionTest.P0, place(ConstructionTest.HOUSE, 20, 28));
        List<CommandResult> houses = results(h);
        assertThat(houses).extracting(CommandResult::outcome).containsOnly(CommandOutcome.ACCEPTED);
    }

    private static PlaceBuilding place(dev.strategos.core.shared.TemplateId template, int x, int z) {
        return new PlaceBuilding(template, x, z, 0, UnitSet.of(), QueueMode.REPLACE);
    }

    private static CommandResult result(SimHarness h) {
        List<CommandResult> r = results(h);
        assertThat(r).hasSize(1);
        return r.get(0);
    }

    private static List<CommandResult> results(SimHarness h) {
        return h.step().events().stream()
                .filter(CommandResult.class::isInstance)
                .map(CommandResult.class::cast)
                .filter(r -> r.kind().equals("strategos:place_building"))
                .toList();
    }
}
