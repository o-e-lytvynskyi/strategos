package dev.strategos.core.sim.unitai;

import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.combat.Engagement;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.OrderQueues;

/**
 * What a spread attack (ORD-05) needs after the start: its attackers share the group id of the command, so the spread
 * set is the targets the attackers still on it hold now, and nothing besides the orders is stored or saved.
 */
final class Spreading {
    private Spreading() {}

    /**
     * The living target, held by another attacker of spread {@code group}, nearest to unit {@code i}, the lower index on
     * a tie; 0 when the set is empty (the unit then takes the nearest enemy it sees, as any attacker does).
     */
    static int repick(MatchState s, int i, int group) {
        OrderQueues orders = s.orders();
        double x = s.motion().x(i);
        double z = s.motion().z(i);
        int best = 0;
        double bestD = Double.POSITIVE_INFINITY;
        for (int j = 0; j < s.entities().highWater(); j++) {
            if (j == i
                    || !s.entities().aliveAt(j)
                    || s.garrisoned(j)
                    || orders.kind(j) != OrderKind.ATTACK
                    || orders.group(j) != group) {
                continue;
            }
            int target = orders.target(j);
            if (Engagement.gone(s, target)) {
                continue;
            }
            int t = Handle.index(target);
            double d = DetMath.length(s.motion().x(t) - x, s.motion().z(t) - z);
            if (d < bestD || (d == bestD && t < Handle.index(best))) {
                bestD = d;
                best = target;
            }
        }
        return best;
    }
}
