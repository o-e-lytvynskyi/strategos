package dev.strategos.core.sim.build;

import static org.assertj.core.api.Assertions.assertThat;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.PlaceholderBlueprints;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.BuildingDestroyed;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.CorePorts;
import dev.strategos.core.sim.ExternalDamage;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.PageRef;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.WorldEffect;
import dev.strategos.core.sim.nav.PageCoord;
import dev.strategos.core.sim.nav.VoxelSlice;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * BLD-01 in L1: an administrator's finished building blocks its footprint, asks the writer for its blocks and is
 * walked round; it falls at its most hit points and gives its ground back; footprints turn; buildings survive a
 * snapshot; a civic centre takes every resource and houses twenty; and the placement's refusals.
 */
class BuildingTest {
    private static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    private static final TemplateId CIVIC_CENTRE = new TemplateId("strategos:building/civic_centre");
    private static final TemplateId BARRACKS = new TemplateId("strategos:building/barracks");

    private static VoxelWorld ground() {
        return VoxelFixtures.flat(64, 64, 16, 1);
    }

    private static SimHarness match() {
        SimHarness h = SimHarness.builder().size(64, 64).build();
        h.publishAll(ground());
        h.step();
        return h;
    }

    /** Steps until the next multiple of {@code pageK}, when the core rebuilds the pages it changed. */
    private static void rebuilt(SimHarness h) {
        long k = h.queries().config().rules().pageK();
        do {
            h.step();
        } while (h.tick() % k != 0);
    }

    private static List<CommandResult> results(TickOutputs out) {
        return out.events().stream()
                .filter(CommandResult.class::isInstance)
                .map(CommandResult.class::cast)
                .toList();
    }

    @Test
    void aSpawnedBuildingBlocksItsFootprintAndAsksForItsBlocks() {
        SimHarness h = match();
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(HOUSE, new FactionSlot(0), 10, 10, 0));
        TickOutputs out = h.step();

        assertThat(results(out))
                .singleElement()
                .satisfies(r -> assertThat(r.firstReason()).isNull());
        List<BuildingView> buildings = h.queries().buildings();
        assertThat(buildings).singleElement().satisfies(b -> {
            assertThat(b.template()).isEqualTo(HOUSE);
            assertThat(b.state()).isEqualTo(BuildingStore.COMPLETE);
            assertThat(b.hitPoints()).isEqualTo(400);
            assertThat(b.baseY())
                    .as("its floor in place of the ground's top block")
                    .isEqualTo(0);
            assertThat(b.housing()).isEqualTo(5);
        });
        Blueprint house = PlaceholderBlueprints.house();
        List<WorldEffect> writes = out.events().stream()
                .filter(WorldEffect.class::isInstance)
                .map(WorldEffect.class::cast)
                .toList();
        assertThat(writes.stream().filter(w -> !w.removes()).count()).isEqualTo(house.count());
        assertThat(writes).allMatch(w -> w.x() >= 10 && w.x() <= 14 && w.z() >= 10 && w.z() <= 14);
        rebuilt(h);
        for (int x = 9; x <= 15; x++) {
            for (int z = 9; z <= 15; z++) {
                boolean inside = x >= 10 && x <= 14 && z >= 10 && z <= 14;
                assertThat(h.queries().surfaces().count(x, z))
                        .as("column (" + x + ", " + z + ")")
                        .isEqualTo(inside ? 0 : 1);
            }
        }
    }

    @Test
    void unitsWalkRoundAFootprint() {
        SimHarness h = match();
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CIVIC_CENTRE, new FactionSlot(0), 20, 20, 0));
        h.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), new Vec2d(16.5, 25.5), 1, 0));
        int walker = h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .findFirst()
                .orElseThrow();
        rebuilt(h);
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(walker), new Vec2d(35.5, 25.5)));
        for (int t = 0; t < 400; t++) {
            h.step();
            UnitView u = h.queries().unit(walker);
            int x = (int) Math.floor(u.x());
            int z = (int) Math.floor(u.z());
            assertThat(x >= 20 && x <= 30 && z >= 20 && z <= 30)
                    .as("tick " + t + " at " + u.x() + ", " + u.z())
                    .isFalse();
        }
        UnitView u = h.queries().unit(walker);
        assertThat(Math.hypot(u.x() - 35.5, u.z() - 25.5))
                .as("arrived round it")
                .isLessThan(1.5);
    }

    @Test
    void aBuildingFallsAtItsMostAndGivesItsGroundBack() {
        SimHarness h = match();
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(HOUSE, new FactionSlot(1), 10, 10, 0));
        h.step();
        rebuilt(h);
        int house = h.queries().buildings().get(0).handle();
        h.damage(new ExternalDamage(house, ExternalDamage.Kind.HACK, 150, ExternalDamage.SourceKind.NONE));
        h.step();
        assertThat(h.queries().buildings().get(0).hitPoints()).isEqualTo(250);

        h.damage(new ExternalDamage(house, ExternalDamage.Kind.LETHAL, 0, ExternalDamage.SourceKind.NONE));
        TickOutputs out = h.step();
        assertThat(out.events()).contains(new BuildingDestroyed(house, new FactionSlot(1)));
        assertThat(h.queries().buildings()).isEmpty();
        long removals = out.events().stream()
                .filter(e -> e instanceof WorldEffect w && w.removes())
                .count();
        Blueprint b = PlaceholderBlueprints.house();
        long aboveFloor = java.util.stream.IntStream.range(0, b.count())
                .filter(k -> b.y(k) > 0)
                .count();
        assertThat(removals).as("every block above its floor").isEqualTo(aboveFloor);
        rebuilt(h);
        assertThat(h.queries().surfaces().page(0, 0)).isEqualTo(ground().grid().page(0, 0));
    }

    @Test
    void aFootprintTurns() {
        Blueprint barracks = PlaceholderBlueprints.barracks();
        Footprint straight = Footprint.of(barracks, 10, 20, 0);
        Footprint turned = Footprint.of(barracks, 10, 20, 1);
        assertThat(straight.spanX()).isEqualTo(9);
        assertThat(turned.spanX()).isEqualTo(7);
        assertThat(turned.spanZ()).isEqualTo(9);
        for (int cell : turned.columns(barracks)) {
            int x = cell >>> 16;
            int z = cell & 0xFFFF;
            assertThat(x).isBetween(10, 16);
            assertThat(z).isBetween(20, 28);
        }
        assertThat(turned.columns(barracks)).hasSize(63);
        // the doorway on the blueprint's north side (z = 0) faces east once turned a quarter clockwise
        assertThat(turned.worldX(4, 0)).isEqualTo(16);
        assertThat(turned.worldZ(4, 0)).isEqualTo(24);
    }

    @Test
    void buildingsSurviveASnapshot() {
        SimHarness h = match();
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(BARRACKS, new FactionSlot(0), 30, 30, 2));
        h.step();
        rebuilt(h);
        MatchSnapshot snapshot = h.core().snapshot();
        MatchSnapshot decoded = MatchSnapshotCodec.decode(
                        new Dynamic<>(JsonOps.INSTANCE, MatchSnapshotCodec.encode(JsonOps.INSTANCE, snapshot)),
                        Migrations.production())
                .getOrThrow();
        assertThat(decoded).isEqualTo(snapshot);
        Map<Long, byte[]> slices = new HashMap<>();
        for (PageRef ref : snapshot.pages()) {
            VoxelSlice slice = ground().slice(new PageCoord(ref.x(), ref.z()));
            slices.put(slice.hash(), slice.encode());
        }
        MatchCore restored = MatchCore.restore(decoded, TemplateSet.builtin(), CorePorts.of(() -> 0L), slices::get);
        assertThat(restored.queries().buildings()).isEqualTo(h.queries().buildings());
        assertThat(restored.queries().surfaces().count(32, 32))
                .as("the footprint blocked again")
                .isZero();
        assertThat(restored.stateHash()).isEqualTo(h.stateHash());
    }

    @Test
    void aCivicCentreTakesEveryResourceAndHousesTwenty() {
        SimHarness h = match();
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CIVIC_CENTRE, new FactionSlot(0), 5, 5, 0));
        h.step();
        BuildingView cc = h.queries().buildings().get(0);
        assertThat(cc.dropsite()).containsExactly("food", "wood", "stone", "iron");
        assertThat(cc.housing()).isEqualTo(20);
        assertThat(cc.maxHitPoints()).isEqualTo(2000);
    }

    @Test
    void placementIsRefusedOffTheSpaceOnAFootprintForAUnitOrToAPlayer() {
        SimHarness h = match();
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(HOUSE, new FactionSlot(0), 10, 10, 0));
        h.step();
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(HOUSE, new FactionSlot(0), 12, 12, 0));
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(HOUSE, new FactionSlot(0), 62, 10, 0));
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(TemplateSet.INFANTRY, new FactionSlot(0), 40, 40, 0));
        h.submit(new PlayerSlot(0), new SpawnBuilding(HOUSE, new FactionSlot(0), 40, 40, 0));
        List<CommandResult> results = results(h.step());
        assertThat(results)
                .extracting(CommandResult::firstReason)
                .containsExactly(
                        RejectReason.INVALID_ARGUMENT,
                        RejectReason.OUT_OF_BOUNDS,
                        RejectReason.UNSUPPORTED,
                        RejectReason.NOT_PERMITTED);
        MatchState s = KernelAccess.state(h.core());
        assertThat(s.buildings().handles()).hasSize(1);
    }
}
