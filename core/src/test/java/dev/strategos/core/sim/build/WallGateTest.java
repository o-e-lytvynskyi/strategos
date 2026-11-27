package dev.strategos.core.sim.build;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Demolish;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.PlaceWall;
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
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * BLD-13.4 in L1: a finished palisade gate is its owner's gate over its middle two columns, however it came to stand;
 * a gate that falls or is demolished is gone from the overlay and its columns are released; the gate and the building
 * survive a save together.
 */
class WallGateTest {
    static final TemplateId CENTRE = new TemplateId("strategos:building/civic_centre");
    static final TemplateId GATE = new TemplateId("strategos:building/palisade_gate");
    static final TemplateId PALISADE = new TemplateId("strategos:building/palisade");

    final SimHarness h =
            SimHarness.builder().size(160, 160).yBand(0, 31, 1).factions(2).build();
    final MatchState s;

    WallGateTest() {
        h.publishAll(VoxelFixtures.flat(160, 160, 32, 1));
        h.step();
        s = KernelAccess.state(h.core());
    }

    int gate(int faction, int x, int z) {
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(GATE, new FactionSlot(faction), x, z, 0));
        h.step();
        int[] handles = s.buildings().handles();
        return handles[handles.length - 1];
    }

    @Test
    void aFinishedGateStandsOverItsMiddleTwoColumnsAsItsOwners() {
        int building = gate(1, 40, 50);
        assertThat(s.play().gates().count()).isEqualTo(1);
        int id = s.play().gates().ids()[0];
        assertThat(s.play().gates().owner(id)).isEqualTo(1);
        assertThat(s.play().gates().columns(id)).containsExactly(41 << 16 | 50, 42 << 16 | 50);
        assertThat(WallGates.columns(s, building)).containsExactly(41 << 16 | 50, 42 << 16 | 50);
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(PALISADE, new FactionSlot(1), 40, 60, 0));
        h.step();
        assertThat(s.play().gates().count()).as("a palisade segment is no gate").isEqualTo(1);
    }

    @Test
    void aGateTurnedAlongZTakesItsMiddleInZ() {
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(GATE, new FactionSlot(0), 40, 50, 1));
        h.step();
        int id = s.play().gates().ids()[0];
        assertThat(s.play().gates().columns(id)).containsExactly(40 << 16 | 51, 40 << 16 | 52);
    }

    @Test
    void aDemolishedGateIsGoneAndItsColumnsAreFree() {
        int building = gate(0, 40, 50);
        assertThat(s.footprintAt(41, 50)).isTrue();
        h.submit(new PlayerSlot(0), new Demolish(building));
        h.step();
        assertThat(s.play().gates().empty()).isTrue();
        assertThat(s.footprintAt(41, 50))
                .as("neither the building's footprint nor the gate's holds")
                .isFalse();
        assertThat(s.footprintAt(40, 50)).isFalse();
    }

    @Test
    void aGateThatLosesItsHealthFallsAndTheOverlayFollows() {
        int building = gate(0, 40, 50);
        s.buildings().setDamage(building, s.buildingMaxHp(building) + 1);
        s.destroyBuilding(building);
        assertThat(s.play().gates().empty()).isTrue();
    }

    @Test
    void aGateChangingHandsChangesOwnerWithIt() {
        int building = gate(0, 40, 50);
        dev.strategos.core.sim.build.BuildingLife.transfer(s, building, 1);
        assertThat(s.play().gates().count()).isEqualTo(1);
        assertThat(s.play().gates().owner(s.play().gates().ids()[0])).isEqualTo(1);
    }

    @Test
    void aGateBuiltFromAFoundationOpensItsWayOnCompletion() {
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, new FactionSlot(0), 40, 70, 0));
        h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "wood", 500));
        h.step();
        h.step();
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.WORKER, new FactionSlot(0), new Vec2d(70, 95), 3, 1));
        int[] workers = h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .toArray();
        h.submit(new PlayerSlot(0), new PlaceWall(PALISADE, 60, 90, 67, 90, List.of(1), UnitSet.of(workers)));
        h.step();
        assertThat(s.play().gates().empty()).as("a foundation is no gate").isTrue();
        assertThat(h.runUntil(() -> s.play().gates().count() == 1, 1_200))
                .as("the gate segment is finished")
                .isTrue();
        assertThat(s.play().gates().columns(s.play().gates().ids()[0])).containsExactly(65 << 16 | 90, 66 << 16 | 90);
    }

    @Test
    void theGateAndItsBuildingSurviveASaveTogether() {
        int building = gate(0, 40, 50);
        h.runTicks(3);
        long saved = h.stateHash();
        SimHarness twin = SimHarness.restore(h.core().snapshot(), TemplateSet.builtin());
        MatchState restored = KernelAccess.state(twin.core());
        assertThat(restored.buildings().alive(building)).isTrue();
        assertThat(restored.play().gates().count()).isEqualTo(1);
        assertThat(restored.play().gates().columns(restored.play().gates().ids()[0]))
                .containsExactly(41 << 16 | 50, 42 << 16 | 50);
        assertThat(twin.stateHash()).isEqualTo(saved);
        assertThat(WallGates.find(restored, WallGates.columns(restored, building)))
                .isNotNegative();
        twin.submit(new PlayerSlot(0), new Demolish(building));
        twin.step();
        assertThat(restored.play().gates().empty())
                .as("the restored gate goes with the restored building")
                .isTrue();
    }

    @Test
    void theDoorsReportWhetherAnAlliedUnitIsNearEachFinishedGate() {
        int building = gate(0, 40, 50);
        h.runTicks(2);
        assertThat(WallGates.doors(s)).hasSize(1);
        GateDoor closed = WallGates.doors(s).get(0);
        assertThat(closed.building()).isEqualTo(building);
        assertThat(closed.open()).isFalse();
        assertThat(closed.rotation()).isZero();
        assertThat(closed.columns()).containsExactly(41 << 16 | 50, 42 << 16 | 50);
        assertThat(closed.baseY()).isEqualTo(s.buildings().baseY(building));
        int[] near = h.spawn(new FactionSlot(0), new Vec2d(41.5, 53.0), 1, 0);
        h.runTicks(2);
        assertThat(WallGates.doors(s).get(0).open())
                .as("an own unit within 3 blocks")
                .isTrue();
        s.removeUnit(near[0]);
        int[] enemy = h.spawn(new FactionSlot(1), new Vec2d(41.5, 52.0), 1, 0);
        h.runTicks(2);
        assertThat(WallGates.doors(s).get(0).open())
                .as("an enemy never opens it")
                .isFalse();
        assertThat(enemy).hasSize(1);
    }
}
