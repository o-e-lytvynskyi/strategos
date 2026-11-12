package dev.strategos.core.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.combat.Engagement;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.sim.unitai.UnitAi;
import dev.strategos.core.testkit.DuelLayout.Kind;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The idle horseman (M5 For the owner 7): in the horsemen-against-archers duel a horseman used to stand 84 ticks
 * beside its target, still walking to a ring place that the horsemen before it had left on the far side of the archer
 * (the places were spaced for a spearman's body, so the large class's neighbours blocked each other). Its places now
 * keep a body's width apart.
 */
class HorsemanStallTest {
    /** The longest a horseman may stand still, approaching a living target, in the 200 seeded duels (it was 84). */
    static final int STALL_MOST = 50;

    @Test
    void aLargeBodyGetsFewerPlacesThanASmallOne() {
        double archer = 0.3;
        double horseman = 0.7;
        double small = 0.4;
        assertThat(Engagement.places(0, small + archer, small))
                .isGreaterThan(Engagement.places(0, horseman + archer, horseman));
        // two neighbours on a ring are at least a body's width apart
        for (int k = 0; k < Engagement.RINGS; k++) {
            double r = horseman + archer + Engagement.GAP + k * Engagement.RING_STEP;
            double apart = 2 * Math.PI * r / Engagement.places(k, horseman + archer, horseman);
            assertThat(apart).as("ring %d", k).isGreaterThanOrEqualTo(2 * horseman - 1e-9);
        }
    }

    @Test
    @Tag("slow")
    void noHorsemanStandsStillBesideItsTargetForLong() {
        int worst = 0;
        long worstSeed = -1;
        for (long seed = 0; seed < 200; seed++) {
            int[] longest = {0};
            Map<Integer, double[]> walkers = new HashMap<>(); // x, z, ticks still
            Duel.run(Kind.HORSEMAN, Kind.ARCHER, seed, 0, h -> {
                MatchQueries q = h.core().queries();
                MatchState s = KernelAccess.state(h.core());
                for (int f = 0; f < 2; f++) {
                    for (int handle : q.unitHandlesOf(new FactionSlot(f))) {
                        UnitView u = q.unit(handle);
                        if (u == null || !u.template().value().endsWith("horseman")) {
                            continue;
                        }
                        double[] w = walkers.computeIfAbsent(handle, k -> new double[] {u.x(), u.z(), 0});
                        boolean still = Math.abs(u.x() - w[0]) + Math.abs(u.z() - w[1]) < 0.02;
                        w[0] = u.x();
                        w[1] = u.z();
                        List<OrderQueues.Order> orders = q.orders(handle);
                        boolean hunting = !orders.isEmpty()
                                && orders.get(0).kind() == OrderKind.ATTACK
                                && q.unit(orders.get(0).target()) != null;
                        boolean approaching =
                                UnitAi.leaf(s.ai().code(Handle.index(handle))).equals(UnitAi.APPROACHING);
                        w[2] = still && hunting && approaching ? w[2] + 1 : 0;
                        longest[0] = Math.max(longest[0], (int) w[2]);
                    }
                }
            });
            if (longest[0] > worst) {
                worst = longest[0];
                worstSeed = seed;
            }
        }
        assertThat(worst)
                .as("longest stand-still while approaching, seed %d", worstSeed)
                .isLessThanOrEqualTo(STALL_MOST);
    }
}
