package dev.strategos.core.sim.build;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.Build;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

/**
 * BLD-06 in L1: k builders repair at half the build speed under the builder rule, to the tick the formula gives (1);
 * free, as D-15 decided (2); and never past the most hit points (3).
 */
class RepairTest {
    @Test
    void repairTimeIsExactForTheFormula() {
        for (int k = 1; k <= 3; k++) {
            SimHarness h = ConstructionTest.match();
            MatchState s = KernelAccess.state(h.core());
            int b = DamageStagesTest.barracks(h);
            double max = s.buildingMaxHp(b);
            s.buildings().setDamage(b, max / 2);
            double gain = max * Construction.work(k, k) / (2.0 * Construction.needed(s, b));
            long expected = (long) Math.ceil((max / 2) / gain);
            long ticks = 0;
            while (s.buildings().damage(b) > 0 && ticks < 1_000_000) {
                for (int n = 0; n < k; n++) {
                    s.buildings().contribute(b, 1);
                }
                Construction.repair(s, b);
                ticks++;
            }
            assertThat(ticks).as("%d builders", k).isEqualTo(expected);
            assertThat(expected)
                    .as("half the build speed: half the building's time for half its hit points, %d builders", k)
                    .isEqualTo((long) Math.ceil(Construction.needed(s, b) / (double) Construction.work(k, k)));
        }
    }

    @Test
    void workersRepairForFreeAndNeverPastTheMost() {
        SimHarness h = ConstructionTest.match();
        MatchState s = KernelAccess.state(h.core());
        int b = DamageStagesTest.barracks(h);
        double max = s.buildingMaxHp(b);
        DamageStagesTest.at(s, b, 0.3);
        int[] workers = ConstructionTest.buildersOnSlots(h, 2);
        long[] stock = stock(s);
        h.submit(ConstructionTest.P0, Build.replace(UnitSet.of(workers), b));
        h.step();
        assertThat(s.orders().kind(dev.strategos.core.shared.Handle.index(workers[0])))
                .as("a repair order")
                .isEqualTo(dev.strategos.core.sim.order.OrderKind.BUILD);
        for (int t = 0; t < 40_000 && s.buildings().damage(b) > 0; t++) {
            h.step();
            assertThat(s.buildings().damage(b)).as("never past the most").isGreaterThanOrEqualTo(0);
        }
        assertThat(s.buildings().damage(b)).as("repaired").isZero();
        assertThat(s.buildingMaxHp(b)).isEqualTo(max);
        assertThat(s.buildings().stage(b)).as("its blocks back").isZero();
        assertThat(stock(s)).as("free (D-15)").containsExactly(stock);
        for (int t = 0; t < 40; t++) {
            h.step();
        }
        assertThat(s.orders().size(dev.strategos.core.shared.Handle.index(workers[0])))
                .as("done, the builders stand")
                .isZero();
    }

    private static long[] stock(MatchState s) {
        long[] out = new long[s.config().rules().economy().size()];
        for (int r = 0; r < out.length; r++) {
            out[r] = s.treasury().stock(0, r);
        }
        return out;
    }
}
