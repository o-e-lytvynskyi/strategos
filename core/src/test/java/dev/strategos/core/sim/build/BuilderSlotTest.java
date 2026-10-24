package dev.strategos.core.sim.build;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.nav.PathService;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import org.junit.jupiter.api.Test;

/**
 * BLD-03: the ground round a building that units are sent to is ground they can stand on. Berry bushes are land to the
 * placement rules but no floor to walk on: a builder never takes a slot on one, and a unit the foundation pushes out
 * of its footprint lands beside the bushes, not on them, and can walk on from there.
 */
class BuilderSlotTest {
    @Test
    void buildersNeverTakeASlotOnABush() {
        SimHarness h = ConstructionTest.match(bushes(29));
        MatchState s = KernelAccess.state(h.core());
        int[] builders = spawn(h, 26.5, 32.5, 3);
        h.submit(
                ConstructionTest.P0,
                new PlaceBuilding(ConstructionTest.HOUSE, 30, 30, 0, UnitSet.of(builders), QueueMode.REPLACE));
        boolean done = false;
        for (int t = 0; t < 3_000 && !done; t++) {
            h.step();
            int[] handles = s.buildings().handles();
            if (handles.length > 1) {
                for (int b : builders) {
                    int i = Handle.index(b);
                    if (s.orders().target(i) == handles[1] && s.orders().slot(i) >= 0) {
                        int cell = s.buildings().slot(handles[1], s.orders().slot(i));
                        assertThat(cell >>> 16)
                                .as("tick %d: a slot on the bushes' column", h.tick())
                                .isNotEqualTo(29);
                    }
                }
                done = s.buildings().state(handles[1]) == BuildingStore.COMPLETE;
            }
        }
        assertThat(done).as("the house goes up").isTrue();
        assertThat(h.invariantViolations()).isEmpty();
    }

    @Test
    void aUnitPushedOutOfAFootprintLandsBesideTheBushes() {
        SimHarness h = ConstructionTest.match(bushes(35));
        MatchState s = KernelAccess.state(h.core());
        int bystander = spawn(h, 34.5, 32.5, 1)[0]; // on the house's east column, the bushes beyond it
        int[] builders = spawn(h, 28.5, 32.5, 1);
        h.submit(
                ConstructionTest.P0,
                new PlaceBuilding(ConstructionTest.HOUSE, 30, 30, 0, UnitSet.of(builders), QueueMode.REPLACE));
        int i = Handle.index(bystander);
        h.runUntil(() -> Math.floor(s.motion().x(i)) != 34, 600);
        assertThat(Math.floor(s.motion().x(i)))
                .as("out of the footprint, past the bushes")
                .isEqualTo(36);
        int from = PathService.startNode(
                s.surfaces(),
                s.motion().x(i),
                s.motion().y(i),
                s.motion().z(i),
                s.config().bounds().minY());
        assertThat(from).as("it stands on a surface it can walk from").isGreaterThanOrEqualTo(0);
    }

    /**
     * The grid takes a new footprint only every {@code pageK} ticks: a unit that walked in meanwhile, here one dropped
     * into a site under way, is put outside it when the grid has it, standing where it can walk from.
     */
    @Test
    void aUnitInsideASiteIsPutOutWhenTheGridHasIt() {
        SimHarness h = ConstructionTest.match();
        MatchState s = KernelAccess.state(h.core());
        int[] builders = spawn(h, 28.5, 32.5, 1);
        h.submit(
                ConstructionTest.P0,
                new PlaceBuilding(ConstructionTest.HOUSE, 30, 30, 0, UnitSet.of(builders), QueueMode.REPLACE));
        h.runUntil(
                () -> s.buildings().handles().length > 1
                        && s.buildings().state(s.buildings().handles()[1]) == BuildingStore.CONSTRUCTING,
                600);
        h.runTicks(s.config().rules().pageK()); // the footprint is in the grid
        int i = Handle.index(spawn(h, 32.5, 32.5, 1)[0]);
        assertThat(s.footprintAt(32, 32)).isTrue();
        h.runTicks(s.config().rules().pageK());
        assertThat(s.footprintAt((int) Math.floor(s.motion().x(i)), (int)
                        Math.floor(s.motion().z(i))))
                .as("out of the site")
                .isFalse();
        int from = PathService.startNode(
                s.surfaces(),
                s.motion().x(i),
                s.motion().y(i),
                s.motion().z(i),
                s.config().bounds().minY());
        assertThat(from).as("it stands on a surface it can walk from").isGreaterThanOrEqualTo(0);
    }

    /** The flat 64² with berry bushes all along column {@code x} of the ring round a house at (30, 30). */
    private static VoxelWorld bushes(int x) {
        VoxelWorld world = VoxelFixtures.flat(64, 64, 32, 1);
        for (int z = 29; z <= 35; z++) {
            world.set(x, 1, z, VoxelFixtures.BUSH);
        }
        return world;
    }

    private static int[] spawn(SimHarness h, double x, double z, int n) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.WORKER, new FactionSlot(0), new Vec2d(x, z), n, 0));
        return h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .toArray();
    }
}
