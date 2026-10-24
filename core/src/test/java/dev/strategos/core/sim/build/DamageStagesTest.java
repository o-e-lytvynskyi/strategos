package dev.strategos.core.sim.build;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.WorldEffect;
import dev.strategos.core.sim.world.WritePriority;
import dev.strategos.core.testkit.SimHarness;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * BLD-05 in L1: at each threshold exactly the ranked blocks fall, in the same order in every match (1); damage to 30 %
 * takes away the third stage's blocks and hit points back to whole put every one back (2); hit points hovering round a
 * threshold change no block until 2 % above it (3); and no block of the two lowest outer-ring layers ever falls (4).
 */
class DamageStagesTest {
    static final TemplateId BARRACKS = new TemplateId("strategos:building/barracks");

    /** A barracks of faction 0 standing whole at (30, 30) in {@link ConstructionTest}'s match; its handle. */
    static int barracks(SimHarness h) {
        MatchState s = KernelAccess.state(h.core());
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(BARRACKS, new FactionSlot(0), 30, 30, 0));
        h.step();
        for (int handle : s.buildings().handles()) {
            if (s.templates().byIndex(s.buildings().template(handle)).id().equals(BARRACKS)) {
                return handle;
            }
        }
        throw new AssertionError("no barracks");
    }

    /** Building {@code handle} left with {@code fraction} of its most: the stage's writes, in order. */
    static List<WorldEffect> at(MatchState s, int handle, double fraction) {
        int before = s.outputs().size();
        s.buildings().setDamage(handle, s.buildingMaxHp(handle) * (1 - fraction));
        DamageStages.update(s, handle);
        List<WorldEffect> out = new ArrayList<>();
        for (OutputEvent e : s.outputs().subList(before, s.outputs().size())) {
            if (e instanceof WorldEffect w && w.priority() == WritePriority.DAMAGE_STAGE) {
                out.add(w);
            }
        }
        return out;
    }

    private static List<String> places(List<WorldEffect> writes) {
        return writes.stream().map(w -> w.x() + "," + w.y() + "," + w.z()).toList();
    }

    @Test
    void eachThresholdRemovesItsRankedBlocks() {
        List<List<String>> runs = new ArrayList<>();
        for (int run = 0; run < 2; run++) {
            SimHarness h = ConstructionTest.match();
            MatchState s = KernelAccess.state(h.core());
            int b = barracks(h);
            Blueprint bp = s.blueprintOf(b);
            int[] ranks = s.damageRanks(bp);
            Footprint fp = s.footprintOf(b);
            List<String> all = new ArrayList<>();
            int fallen = 0;
            for (double[] step : new double[][] {{0.9, 0}, {0.84, 1}, {0.64, 2}, {0.34, 3}}) {
                List<WorldEffect> writes = at(s, b, step[0]);
                int stage = (int) step[1];
                int want = DamageStages.fallen(stage, ranks.length, 0.85, 0.65, 0.35);
                assertThat(s.buildings().stage(b)).isEqualTo(stage);
                assertThat(writes).as("at stage %d", stage).hasSize(want - fallen);
                for (int r = fallen; r < want; r++) {
                    int k = ranks[r];
                    WorldEffect w = writes.get(r - fallen);
                    assertThat(w.state()).isEqualTo(WorldEffect.AIR);
                    assertThat(w.x()).isEqualTo(fp.worldX(bp.x(k), bp.z(k)));
                    assertThat(w.y()).isEqualTo(s.buildings().baseY(b) + bp.y(k));
                    assertThat(w.z()).isEqualTo(fp.worldZ(bp.x(k), bp.z(k)));
                }
                fallen = want;
                all.addAll(places(writes));
            }
            assertThat(fallen)
                    .as("65 % of the falling blocks down at the third stage")
                    .isEqualTo((int) Math.round(ranks.length * 0.65));
            runs.add(all);
        }
        assertThat(runs.get(1)).as("the same order in another match").isEqualTo(runs.get(0));
    }

    @Test
    void thirtyPercentAndBack() {
        SimHarness h = ConstructionTest.match();
        MatchState s = KernelAccess.state(h.core());
        int b = barracks(h);
        List<WorldEffect> down = at(s, b, 0.30);
        assertThat(s.buildings().stage(b)).isEqualTo(3);
        assertThat(down).hasSize(DamageStages.fallen(3, s.damageRanks(s.blueprintOf(b)).length, 0.85, 0.65, 0.35));
        List<WorldEffect> back = at(s, b, 1.0);
        assertThat(s.buildings().stage(b)).isZero();
        assertThat(places(back))
                .as("every block back, the last fallen first")
                .isEqualTo(places(down).reversed());
        assertThat(back).allSatisfy(w -> assertThat(w.state()).isNotEqualTo(WorldEffect.AIR));
    }

    @Test
    void hysteresisOfTwoPercent() {
        SimHarness h = ConstructionTest.match();
        MatchState s = KernelAccess.state(h.core());
        int b = barracks(h);
        assertThat(at(s, b, 0.849)).as("below 85 %").isNotEmpty();
        assertThat(at(s, b, 0.851)).as("just over 85 %").isEmpty();
        assertThat(at(s, b, 0.849)).as("under again").isEmpty();
        assertThat(at(s, b, 0.869)).as("1.9 % over").isEmpty();
        assertThat(s.buildings().stage(b)).isOne();
        assertThat(at(s, b, 0.87)).as("2 % over").isNotEmpty();
        assertThat(s.buildings().stage(b)).isZero();
    }

    @Test
    void theBottomRingStays() {
        SimHarness h = ConstructionTest.match();
        MatchState s = KernelAccess.state(h.core());
        int b = barracks(h);
        Blueprint bp = s.blueprintOf(b);
        Footprint fp = s.footprintOf(b);
        int baseY = s.buildings().baseY(b);
        List<WorldEffect> down = at(s, b, 0.01);
        assertThat(down).isNotEmpty();
        for (WorldEffect w : down) {
            int y = w.y() - baseY;
            boolean edge = w.x() == fp.anchorX()
                    || w.x() == fp.anchorX() + fp.spanX() - 1
                    || w.z() == fp.anchorZ()
                    || w.z() == fp.anchorZ() + fp.spanZ() - 1;
            assertThat(y <= 1 && edge).as("%s fell from the bottom ring", w).isFalse();
        }
        int ring = 0;
        for (int k = 0; k < bp.count(); k++) {
            boolean edge = bp.x(k) == 0 || bp.z(k) == 0 || bp.x(k) == bp.sizeX() - 1 || bp.z(k) == bp.sizeZ() - 1;
            ring += bp.y(k) <= 1 && edge ? 1 : 0;
        }
        assertThat(s.damageRanks(bp).length)
                .as("every block but the bottom ring's falls in the end")
                .isEqualTo(bp.count() - ring);
    }
}
