package dev.strategos.core.sim.build;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.CancelBuilding;
import dev.strategos.core.protocol.Demolish;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * BLD-09.1 in L1: a cancelled foundation gives back its whole cost, a construction cancelled at x thousandths built
 * {@code cost × (1000 − x) ÷ 1000} rounded down, and a finished building is demolished, never cancelled; demolishing
 * gives nothing back and takes its housing.
 */
class CancelTest {
    static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    static final PlayerSlot P0 = new PlayerSlot(0);

    private static SimHarness match() {
        SimHarness h = SimHarness.builder().size(64, 64).yBand(0, 31, 1).build();
        h.publishAll(VoxelFixtures.flat(64, 64, 32, 1));
        h.submit(
                PlayerSlot.ADMIN,
                new SpawnBuilding(new TemplateId("strategos:building/civic_centre"), new FactionSlot(0), 2, 2, 0));
        h.submit(PlayerSlot.ADMIN, new dev.strategos.core.protocol.Grant(new FactionSlot(0), "wood", 1_000));
        h.step();
        return h;
    }

    private static int house(MatchState s) {
        for (int handle : s.buildings().handles()) {
            if (s.templates().byIndex(s.buildings().template(handle)).id().equals(HOUSE)) {
                return handle;
            }
        }
        return 0;
    }

    private static CommandResult result(SimHarness h, String kind) {
        List<CommandResult> r = h.step().events().stream()
                .filter(CommandResult.class::isInstance)
                .map(CommandResult.class::cast)
                .filter(c -> c.kind().equals(kind))
                .toList();
        assertThat(r).hasSize(1);
        return r.get(0);
    }

    @Test
    void theRefundIsTheDocumentedShare() {
        assertThat(BuildingLife.refund(new long[] {80_000, 0, 1}, 0)).containsExactly(80_000, 0, 1);
        assertThat(BuildingLife.refund(new long[] {80_000, 0, 1}, 333)).containsExactly(53_360, 0, 0);
        assertThat(BuildingLife.refund(new long[] {80_000}, 1_000)).containsExactly(0);

        SimHarness h = match();
        MatchState s = KernelAccess.state(h.core());
        long before = s.treasury().stock(0, 1);
        h.submit(P0, new PlaceBuilding(HOUSE, 30, 30, 0, UnitSet.of(), QueueMode.REPLACE));
        h.step();
        assertThat(s.treasury().stock(0, 1)).as("paid").isEqualTo(before - 80_000);
        h.submit(P0, new CancelBuilding(house(s)));
        h.step();
        assertThat(house(s)).as("gone").isZero();
        assertThat(s.treasury().stock(0, 1)).as("a foundation: all of it back").isEqualTo(before);

        int[] workers = ConstructionTest.buildersOnSlots(h, 2);
        h.submit(P0, new PlaceBuilding(HOUSE, 30, 30, 0, UnitSet.of(workers), QueueMode.REPLACE));
        h.step();
        int site = house(s);
        for (int t = 0; t < 20_000 && s.buildings().progress(site) < 400; t++) {
            h.step();
        }
        long progress = s.buildings().progress(site);
        assertThat(progress).isBetween(400L, 999L);
        long paid = s.treasury().stock(0, 1);
        h.submit(P0, new CancelBuilding(site));
        h.step();
        assertThat(s.treasury().stock(0, 1) - paid)
                .as("cancelled at %d thousandths", progress)
                .isEqualTo(80_000 * (1_000 - progress) / 1_000);
    }

    @Test
    void aFinishedBuildingIsDemolishedForNothing() {
        SimHarness h = match();
        MatchState s = KernelAccess.state(h.core());
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(HOUSE, new FactionSlot(0), 30, 30, 0));
        h.step();
        int house = house(s);
        int cap = s.population().cap(0);
        long wood = s.treasury().stock(0, 1);
        h.submit(P0, new CancelBuilding(house));
        assertThat(result(h, "strategos:cancel_building").firstReason()).isEqualTo(RejectReason.UNSUPPORTED);
        h.submit(new PlayerSlot(1), new Demolish(house));
        assertThat(result(h, "strategos:demolish").firstReason()).isEqualTo(RejectReason.NOT_OWNER);
        h.submit(P0, new Demolish(house));
        assertThat(result(h, "strategos:demolish").accepted()).isOne();
        assertThat(house(s)).as("gone").isZero();
        assertThat(s.population().cap(0)).as("its housing with it").isEqualTo(cap - 5);
        assertThat(s.treasury().stock(0, 1)).as("no refund").isEqualTo(wood);
        assertThat(s.buildings().handles()).as("the centre stands").hasSize(1);
        assertThat(BuildingStore.isBuilding(house)).isTrue();
    }
}
