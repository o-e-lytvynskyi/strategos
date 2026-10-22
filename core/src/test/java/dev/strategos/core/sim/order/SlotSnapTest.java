package dev.strategos.core.sim.order;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.SlotLayout;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.nav.PathService;
import dev.strategos.core.sim.nav.SurfaceGrid;
import dev.strategos.core.testkit.Scenario;
import dev.strategos.core.testkit.ScenarioReport;
import dev.strategos.core.testkit.Scenarios;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ORD-03.1 (spec §5.9): a group's places, snapped on terrain, stand on surfaces of the target's component (never in a
 * walled pen beside it), at least 0.8 apart, the same in two runs; on the reference formations, a block heading along
 * its rows and a line across the way, no two members' straight ways to their places cross (a file or a slant may:
 * spec §1.6 D row 134); and fifty sent as one group all settle by their places, from sixteen scatters of their spawn.
 */
class SlotSnapTest {
    private static final Vec2d TARGET = new Vec2d(30.5, 24.5);

    @Test
    void placesAreStandableInTheTargetsComponentSpacedAndDeterministic() {
        List<Vec2d> first = places(pen(), grid(6, 6, 5, 22));
        List<Vec2d> second = places(pen(), grid(6, 6, 5, 22));

        assertThat(first).isEqualTo(second);
        SimHarness h = harness(pen());
        h.step(); // the pages are published at the first step
        SurfaceGrid surfaces = KernelAccess.state(h.core()).surfaces();
        int home = component(surfaces, TARGET.x(), TARGET.z());
        for (int k = 0; k < first.size(); k++) {
            Vec2d p = first.get(k);
            assertThat(component(surfaces, p.x(), p.z())).as("place %s", p).isEqualTo(home);
            for (int j = 0; j < k; j++) {
                assertThat(Math.hypot(
                                p.x() - first.get(j).x(), p.z() - first.get(j).z()))
                        .isGreaterThan(SlotLayout.MIN_SPACING - 1e-9);
            }
        }
    }

    @Test
    void fiftySettleInTheirPlacesFromSixteenSpawns() {
        StringBuilder failed = new StringBuilder();
        for (long seed = 50; seed < 66; seed++) {
            Scenario scenario = Scenarios.groupOf50Slots(seed);
            ScenarioReport report = SimHarness.forScenario(scenario).play(scenario);
            if (!report.passed()) {
                failed.append(System.lineSeparator())
                        .append("seed ")
                        .append(seed)
                        .append(": ")
                        .append(report.failure());
            }
        }

        assertThat(failed.toString())
                .as("the spawns whose fifty did not settle")
                .isEmpty();
    }

    @Test
    void noWaysCrossOnTheReferenceFormations() {
        List<List<Vec2d>> formations = List.of(
                grid(5, 5, 4.5, 20.5), // a block, heading along its rows
                grid(1, 12, 6.5, 13.5)); // a line across the way
        for (List<Vec2d> starts : formations) {
            List<Vec2d> ends = places(VoxelFixtures.flat(64, 48, 8, 1), starts);
            for (int a = 0; a < starts.size(); a++) {
                for (int b = 0; b < a; b++) {
                    assertThat(cross(starts.get(a), ends.get(a), starts.get(b), ends.get(b)))
                            .as("the ways of %s and %s", starts.get(a), starts.get(b))
                            .isFalse();
                }
            }
        }
    }

    /** Units spawned at {@code starts}, then one Move for all to the target: each one's place, in spawn order. */
    private static List<Vec2d> places(VoxelWorld world, List<Vec2d> starts) {
        SimHarness h = harness(world);
        for (Vec2d start : starts) {
            h.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), start, 1, 0));
        }
        h.step();
        int[] units = h.queries().unitHandles();
        assertThat(units).hasSameSizeAs(starts.toArray());
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(units), TARGET));
        h.step();
        List<Vec2d> out = new ArrayList<>();
        for (int unit : units) {
            OrderQueues.Order order = h.queries().orders(unit).get(0);
            assertThat(order.slot()).as("every member has a place").isPositive();
            out.add(new Vec2d(order.x(), order.z()));
        }
        return out;
    }

    /** {@code columns} by {@code rows} starts two blocks apart from (x, z), rows along Z. */
    private static List<Vec2d> grid(int columns, int rows, double x, double z) {
        List<Vec2d> out = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                out.add(new Vec2d(x + 2 * c, z + 2 * r));
            }
        }
        return out;
    }

    /** Flat ground with a walled pen, three high, round x 32..34 and z 22..26, just ahead of the target. */
    private static VoxelWorld pen() {
        VoxelWorld w = VoxelFixtures.flat(64, 48, 8, 1);
        w.fill(31, 1, 21, 35, 3, 21, VoxelFixtures.SOLID);
        w.fill(31, 1, 27, 35, 3, 27, VoxelFixtures.SOLID);
        w.fill(31, 1, 21, 31, 3, 27, VoxelFixtures.SOLID);
        w.fill(35, 1, 21, 35, 3, 27, VoxelFixtures.SOLID);
        return w;
    }

    private static SimHarness harness(VoxelWorld world) {
        SimHarness h = SimHarness.builder().size(world.sizeX(), world.sizeZ()).build();
        h.publishAll(world);
        return h;
    }

    private static int component(SurfaceGrid grid, double x, double z) {
        int node = PathService.startNode(grid, x, 1, z, 0);
        assertThat(node).as("a surface at %s, %s", x, z).isNotNegative();
        return grid.component(node);
    }

    /** Whether segments ab and cd cross at a point inside both. */
    private static boolean cross(Vec2d a, Vec2d b, Vec2d c, Vec2d d) {
        double d1 = side(c, d, a);
        double d2 = side(c, d, b);
        double d3 = side(a, b, c);
        double d4 = side(a, b, d);
        return d1 * d2 < 0 && d3 * d4 < 0;
    }

    private static double side(Vec2d p, Vec2d q, Vec2d r) {
        return (q.x() - p.x()) * (r.z() - p.z()) - (q.z() - p.z()) * (r.x() - p.x());
    }
}
