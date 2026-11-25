package dev.strategos.core.sim.build;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.PlaceWall;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.nav.GateOverlay;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * BLD-13.2 in L1 (M9 spec §2.10): PlaceWall lays one foundation a segment, each with its own placement check
 * (territory included) and its own cost; a refused segment leaves a gap and the outcome counts it; gates are the gate
 * template; builders are sent to every segment in order.
 */
class PlaceWallTest {
    static final TemplateId CENTRE = new TemplateId("strategos:building/civic_centre");
    static final TemplateId PALISADE = new TemplateId("strategos:building/palisade");
    static final TemplateId GATE = new TemplateId("strategos:building/palisade_gate");
    static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    static final PlayerSlot P0 = new PlayerSlot(0);
    static final int WOOD = 1; // the resources' order: food, wood, stone, iron

    final SimHarness h =
            SimHarness.builder().size(320, 160).yBand(0, 31, 1).factions(2).build();
    final MatchState s;

    PlaceWallTest() {
        h.publishAll(VoxelFixtures.flat(320, 160, 32, 1));
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, new FactionSlot(0), 40, 70, 0)); // land to x 101
        h.step();
        h.step();
        s = KernelAccess.state(h.core());
    }

    void fund(int wood) {
        h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "wood", wood));
        h.step();
        assertThat(s.treasury().stock(0, WOOD)).isEqualTo(wood * 1_000L);
    }

    CommandResult wall(TemplateId template, int x1, int z1, int x2, int z2, List<Integer> gates, UnitSet builders) {
        h.submit(P0, new PlaceWall(template, x1, z1, x2, z2, gates, builders));
        h.step();
        return h.results().get(h.results().size() - 1);
    }

    /** The foundations, in the order they were laid: handle, template id, corner x, corner z, rotation. */
    List<int[]> foundations() {
        List<int[]> out = new ArrayList<>();
        for (int handle : s.buildings().handles()) {
            String id =
                    s.templates().byIndex(s.buildings().template(handle)).id().value();
            if (id.contains("palisade")) {
                var at = s.footprintOf(handle);
                out.add(new int[] {handle, id.endsWith("_gate") ? 1 : 0, at.anchorX(), at.anchorZ(), at.rotation()});
            }
        }
        return out;
    }

    @Test
    void aLineIsOneFoundationASegmentEndToEndAndPaidFromTheTreasury() {
        fund(5_000);
        long before = s.treasury().stock(0, WOOD);
        CommandResult result = wall(PALISADE, 60, 90, 75, 90, List.of(), UnitSet.of());
        assertThat(result.outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(result.accepted()).isEqualTo(4);
        assertThat(result.rejected()).isZero();
        List<int[]> placed = foundations();
        assertThat(placed).extracting(f -> f[2]).containsExactly(60, 64, 68, 72);
        assertThat(placed).allMatch(f -> f[3] == 90 && f[4] == 0 && f[1] == 0);
        assertThat(s.buildings().state(placed.get(0)[0])).isEqualTo(BuildingStore.FOUNDATION);
        assertThat(before - s.treasury().stock(0, WOOD)).as("14 wood a segment").isEqualTo(4 * 14_000L);
    }

    @Test
    void aLineAlongZIsTurnedAQuarter() {
        fund(5_000);
        CommandResult result = wall(PALISADE, 70, 100, 70, 87, List.of(), UnitSet.of()); // towards lower Z
        assertThat(result.accepted()).isEqualTo(4);
        assertThat(foundations()).allMatch(f -> f[4] == 1 && f[2] == 70);
        assertThat(foundations())
                .extracting(f -> f[3])
                .as("segments cut from the pressed end")
                .containsExactly(97, 93, 89, 85);
    }

    @Test
    void theNamedSegmentsAreGateTemplatesAndCostMore() {
        fund(5_000);
        long before = s.treasury().stock(0, WOOD);
        CommandResult result = wall(PALISADE, 60, 90, 75, 90, List.of(1, 3), UnitSet.of());
        assertThat(result.outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(foundations()).extracting(f -> f[1]).containsExactly(0, 1, 0, 1);
        assertThat(before - s.treasury().stock(0, WOOD)).isEqualTo((2 * 14 + 2 * 20) * 1_000L);
    }

    @Test
    void aSegmentOutsideTheTerritoryIsRefusedLeavingAGapAndTheRestStands() {
        fund(5_000);
        CommandResult result = wall(PALISADE, 90, 90, 130, 90, List.of(), UnitSet.of());
        assertThat(result.outcome()).isEqualTo(CommandOutcome.PARTIAL);
        assertThat(result.firstReason()).isEqualTo(RejectReason.OUTSIDE_TERRITORY);
        assertThat(result.accepted()).isPositive();
        assertThat(result.rejected()).isPositive();
        assertThat(result.accepted() + result.rejected()).isEqualTo(11);
        assertThat(foundations()).hasSize(result.accepted());
        assertThat(foundations()).extracting(f -> f[2]).isSorted();
        assertThat(foundations().get(0)[2]).isEqualTo(90);
    }

    @Test
    void aLineWhollyOutsideIsRejectedAndPaysNothing() {
        fund(5_000);
        long before = s.treasury().stock(0, WOOD);
        CommandResult result = wall(PALISADE, 200, 120, 230, 120, List.of(), UnitSet.of());
        assertThat(result.outcome()).isEqualTo(CommandOutcome.REJECTED);
        assertThat(result.firstReason()).isEqualTo(RejectReason.OUTSIDE_TERRITORY);
        assertThat(result.accepted()).isZero();
        assertThat(s.treasury().stock(0, WOOD)).isEqualTo(before);
    }

    @Test
    void aSegmentOnABuildingIsRefusedAndItsNeighboursStand() {
        fund(5_000);
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(HOUSE, new FactionSlot(0), 66, 89, 0)); // 5 x 5 from (66, 89)
        h.step();
        CommandResult result = wall(PALISADE, 60, 90, 83, 90, List.of(), UnitSet.of());
        assertThat(result.outcome()).isEqualTo(CommandOutcome.PARTIAL);
        assertThat(result.firstReason()).isEqualTo(RejectReason.BLOCKED);
        assertThat(foundations())
                .extracting(f -> f[2])
                .as("60 and 64's cells are free, 64..68 hit the house")
                .containsExactly(60, 72, 76, 80);
    }

    @Test
    void whenTheStockRunsOutTheLaterSegmentsAreRefusedForIt() {
        fund(30);
        CommandResult result = wall(PALISADE, 60, 90, 75, 90, List.of(), UnitSet.of());
        assertThat(result.outcome()).isEqualTo(CommandOutcome.PARTIAL);
        assertThat(result.accepted()).isEqualTo(2);
        assertThat(result.rejected()).isEqualTo(2);
        assertThat(result.firstReason()).isEqualTo(RejectReason.NOT_ENOUGH_RESOURCES);
        assertThat(result.missing()).as("what the third segment lacks").isNotEmpty();
        assertThat(s.treasury().stock(0, WOOD)).isEqualTo(2_000L);
    }

    @Test
    void buildersGoToEverySegmentInOrder() {
        fund(5_000);
        h.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(
                        dev.strategos.core.content.TemplateSet.WORKER, new FactionSlot(0), new Vec2d(62, 95), 2, 1));
        int[] workers = h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .toArray();
        CommandResult result = wall(PALISADE, 60, 90, 75, 90, List.of(), UnitSet.of(workers));
        assertThat(result.accepted()).isEqualTo(4);
        for (int worker : workers) {
            assertThat(s.orders().size(Handle.index(worker)))
                    .as("a build order on each segment")
                    .isEqualTo(4);
        }
    }

    @Test
    void badArgumentsAreRefusedWholeAndPayNothing() {
        fund(5_000);
        long before = s.treasury().stock(0, WOOD);
        assertThat(wall(PALISADE, 60, 90, 60 + 4 * 65, 90, List.of(), UnitSet.of())
                        .firstReason())
                .as("more than 64 segments")
                .isEqualTo(RejectReason.INVALID_ARGUMENT);
        assertThat(wall(PALISADE, Integer.MIN_VALUE, 90, Integer.MAX_VALUE, 90, List.of(), UnitSet.of())
                        .firstReason())
                .as("a run longer than an int")
                .isEqualTo(RejectReason.INVALID_ARGUMENT);
        assertThat(wall(PALISADE, 90, Integer.MAX_VALUE, 90, Integer.MIN_VALUE, List.of(), UnitSet.of())
                        .firstReason())
                .isEqualTo(RejectReason.INVALID_ARGUMENT);
        assertThat(wall(PALISADE, 60, 90, 75, 90, List.of(4), UnitSet.of()).firstReason())
                .as("a gate index past the last segment")
                .isEqualTo(RejectReason.INVALID_ARGUMENT);
        assertThat(wall(PALISADE, 60, 90, 75, 90, List.of(-1), UnitSet.of()).firstReason())
                .isEqualTo(RejectReason.INVALID_ARGUMENT);
        assertThat(wall(HOUSE, 60, 90, 75, 90, List.of(), UnitSet.of()).firstReason())
                .as("a house is no wall")
                .isEqualTo(RejectReason.UNSUPPORTED);
        assertThat(wall(new TemplateId("strategos:building/nothing"), 60, 90, 75, 90, List.of(), UnitSet.of())
                        .firstReason())
                .isEqualTo(RejectReason.UNKNOWN_TEMPLATE);
        assertThat(s.treasury().stock(0, WOOD)).isEqualTo(before);
        assertThat(foundations()).isEmpty();
    }

    @Test
    void aGateSegmentPastTheGateLimitIsRefusedNotLaidAsAPlainWall() {
        fund(5_000);
        for (int k = 0; k < GateOverlay.MAX_GATES - 1; k++) {
            s.addGate(0, new int[] {200 << 16 | 2 * k, 200 << 16 | 2 * k + 1}); // 63 gates stand elsewhere
        }
        CommandResult result = wall(PALISADE, 60, 90, 75, 90, List.of(1, 3), UnitSet.of());
        assertThat(result.outcome()).isEqualTo(CommandOutcome.PARTIAL);
        assertThat(result.accepted()).isEqualTo(3);
        assertThat(result.rejected()).isEqualTo(1);
        assertThat(result.firstReason()).isEqualTo(RejectReason.LIMIT);
        assertThat(foundations())
                .extracting(f -> f[1])
                .as("one gate, the rest plain")
                .containsExactly(0, 1, 0);
        CommandResult again = wall(PALISADE, 60, 94, 75, 94, List.of(0), UnitSet.of());
        assertThat(again.firstReason()).as("the queued gate foundation counts").isEqualTo(RejectReason.LIMIT);
        assertThat(again.accepted()).isEqualTo(3);
    }

    @Test
    void anAdminOrAnObserverMayNotPlaceWalls() {
        fund(5_000);
        h.submit(PlayerSlot.ADMIN, new PlaceWall(PALISADE, 60, 90, 75, 90, List.of(), UnitSet.of()));
        h.step();
        assertThat(h.results().get(h.results().size() - 1).firstReason()).isEqualTo(RejectReason.NOT_PERMITTED);
    }

    @Test
    void theCellsOfAllTheSegmentsAreDistinct() {
        fund(5_000);
        wall(PALISADE, 60, 90, 87, 90, List.of(2), UnitSet.of());
        TreeSet<Integer> cells = new TreeSet<>();
        int total = 0;
        for (int[] f : foundations()) {
            for (int c : s.footprintOf(f[0]).columns(s.blueprintOf(f[0]))) {
                cells.add(c);
                total++;
            }
        }
        assertThat(cells).hasSize(total).hasSize(28);
    }
}
