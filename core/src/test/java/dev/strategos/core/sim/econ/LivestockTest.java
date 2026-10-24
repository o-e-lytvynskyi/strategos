package dev.strategos.core.sim.econ;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CarcassGone;
import dev.strategos.core.sim.CarcassLeft;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.nav.MaterialKind;
import dev.strategos.core.sim.order.OrderKind;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ECO-16 in L1: a sheep slaughtered and gathered by workers yields exactly its template's 100 food, its carcass left at
 * its death and gone when empty (1); animals are Gaia's, cost no population, and stand under a spearman's blows, never
 * moving away (the crowd may jostle them), while a spearman beside them never engages them by itself (2).
 */
class LivestockTest {
    private static final TemplateId SHEEP = new TemplateId("strategos:unit/sheep");

    @Test
    void killAndGatherYieldTheTemplate() {
        EconScene e = EconScene.flat(48, 48).build();
        e.civicCentre(0, 30, 30);
        int sheep = e.units(SHEEP, e.s.gaia(), new Vec2d(16.5, 16.5), 1, 0)[0];
        int node = Livestock.nodeOf(e.s, sheep);
        assertThat(node).as("its meat from its spawn").isNotZero();
        assertThat(e.s.nodes().kind(node)).isEqualTo(MaterialKind.CARCASS);
        assertThat(e.s.nodes().amount(node)).isEqualTo(Amount.of(100));
        int[] workers = e.workers(0, new Vec2d(20.5, 20.5), 2, 1);
        e.h.submit(EconScene.P0, Attack.replace(UnitSet.of(workers), sheep));
        e.h.step();
        assertThat(e.s.orders().kind(e.index(workers[0])))
                .as("a worker cannot fight: it gathers the animal")
                .isEqualTo(OrderKind.GATHER);
        List<OutputEvent> carcass = new ArrayList<>();
        for (int t = 0; t < 6_000 && !e.s.nodes().exhausted(node); t++) {
            TickOutputs out = e.h.step();
            for (OutputEvent ev : out.events()) {
                if (ev instanceof CarcassLeft || ev instanceof CarcassGone) {
                    carcass.add(ev);
                }
            }
        }
        assertThat(e.s.entities().isAlive(sheep)).as("slaughtered").isFalse();
        assertThat(e.s.nodes().exhausted(node)).as("gathered to the last").isTrue();
        assertThat(carcass).containsExactly(new CarcassLeft(sheep, node), new CarcassGone(node));
        for (int k = 0; k < 600; k++) {
            e.h.step(); // the last loads home
        }
        long carried = 0;
        for (int w : workers) {
            carried += e.s.carry().resource(e.index(w)) == EconScene.FOOD
                    ? e.s.carry().amount(e.index(w))
                    : 0;
        }
        assertThat(e.gathered(0, EconScene.FOOD) + carried)
                .as("the template's meat, no more")
                .isEqualTo(Amount.of(100));
    }

    @Test
    void animalsNeverFleeAndCostNoPopulation() {
        EconScene e = EconScene.flat(48, 48).build();
        int used = e.s.population().used(0);
        int[] herd = e.units(SHEEP, e.s.gaia(), new Vec2d(16.5, 16.5), 3, 2);
        assertThat(herd).hasSize(3);
        assertThat(e.s.population().used(0)).as("no population").isEqualTo(used);
        assertThat(e.s.identity().faction(e.index(herd[0]))).as("Gaia's").isEqualTo(2);
        int[] idle = e.units(dev.strategos.core.content.TemplateSet.INFANTRY, 0, new Vec2d(19.5, 16.5), 1, 0);
        for (int t = 0; t < 100; t++) {
            e.h.step();
        }
        assertThat(e.s.orders().size(e.index(idle[0])))
                .as("no spearman engages an animal by itself")
                .isZero();
        double[][] at = new double[herd.length][];
        for (int k = 0; k < herd.length; k++) {
            at[k] = new double[] {e.s.motion().x(e.index(herd[k])), e.s.motion().z(e.index(herd[k]))};
        }
        e.h.submit(EconScene.P0, Attack.replace(UnitSet.of(idle), herd[0]));
        for (int t = 0; t < 2_000 && e.s.entities().isAlive(herd[0]); t++) {
            e.h.step();
            for (int k = 0; k < herd.length; k++) {
                if (!e.s.entities().isAlive(herd[k])) {
                    continue;
                }
                int i = e.index(herd[k]);
                assertThat(e.s.orders().kind(i)).as("sheep %d never flees", k).isNotEqualTo(OrderKind.FLEE);
                assertThat(Math.hypot(e.s.motion().x(i) - at[k][0], e.s.motion().z(i) - at[k][1]))
                        .as("sheep %d stays where it stood, jostled at most", k)
                        .isLessThan(0.5);
            }
        }
        assertThat(e.s.entities().isAlive(herd[0])).as("killed").isFalse();
        assertThat(e.s.nodes().amount(Livestock.nodeOf(e.s, herd[1]))).isPositive();
    }
}
