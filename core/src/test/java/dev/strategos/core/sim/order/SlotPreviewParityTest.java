package dev.strategos.core.sim.order;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.client.order.SlotPreview;
import dev.strategos.core.content.StartSlot;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.testkit.ProvingGround;
import dev.strategos.core.testkit.SimHarness;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * UI-05.1–2: the client's preview and the server's slot planner give the same places for the same group order; on the
 * proving ground's terrain the server snaps each place to a surface, at most 3 columns away, so every server place
 * lies within that of a preview place (spec M2 §1.6).
 */
class SlotPreviewParityTest {
    @Test
    void withoutTerrainThePlacesAreEqual() {
        SimHarness h = SimHarness.builder().size(128, 128).build();
        int[] units = h.spawn(new FactionSlot(0), new Vec2d(30.5, 40.5), 37, 6);
        List<double[]> before = positions(h, units);

        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(units), new Vec2d(90.5, 70.5)));
        h.step();

        double[][] preview = SlotPreview.places(before, radius(), 90.5, 70.5, 128, 128);
        assertThat(nearest(serverPlaces(h, units), preview)).isLessThan(1e-9);
    }

    @Test
    void onTerrainTheServerSnapsWithin3Columns() {
        ProvingGround ground = ProvingGround.generate();
        SimHarness h = SimHarness.builder()
                .size(ProvingGround.SIZE, ProvingGround.SIZE)
                .yBand(0, ProvingGround.HEIGHT, 16)
                .build();
        h.publishAll(ground.voxels());
        h.step();
        StartSlot west = ground.startSlots().get(0);
        int[] units = h.spawn(new FactionSlot(0), new Vec2d(west.x() + 0.5, west.z() + 0.5), 50, 6);
        h.runTicks(5);
        List<double[]> before = positions(h, units);
        Vec2d target = new Vec2d(150.5, 150.5);

        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(units), target));
        h.step();

        double[][] preview =
                SlotPreview.places(before, radius(), target.x(), target.z(), ProvingGround.SIZE, ProvingGround.SIZE);
        assertThat(nearest(serverPlaces(h, units), preview)).isLessThanOrEqualTo(3 * Math.sqrt(2) + 1e-9);
    }

    private static double radius() {
        return TemplateSet.builtin().byId(TemplateSet.DEBUG_WALKER).radius();
    }

    private static List<double[]> positions(SimHarness h, int[] units) {
        List<double[]> out = new ArrayList<>();
        for (int u : units) {
            UnitView v = h.queries().unit(u);
            assertThat(v).isNotNull();
            out.add(new double[] {v.x(), v.z()});
        }
        return out;
    }

    private static List<double[]> serverPlaces(SimHarness h, int[] units) {
        List<double[]> out = new ArrayList<>();
        for (int u : units) {
            var orders = h.queries().orders(u);
            assertThat(orders).isNotEmpty();
            out.add(new double[] {orders.get(0).x(), orders.get(0).z()});
        }
        return out;
    }

    /** The farthest any server place lies from its nearest preview place. */
    private static double nearest(List<double[]> server, double[][] preview) {
        double worst = 0;
        for (double[] s : server) {
            double best = Double.POSITIVE_INFINITY;
            for (double[] p : preview) {
                best = Math.min(best, Math.hypot(s[0] - p[0], s[1] - p[1]));
            }
            worst = Math.max(worst, best);
        }
        return worst;
    }
}
