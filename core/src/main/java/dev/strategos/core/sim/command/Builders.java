package dev.strategos.core.sim.command;

import dev.strategos.core.content.Footprint;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.OrderQueues;

/**
 * Builders sent to a building, for {@link PlaceBuildingRule} and {@link BuildRule}: each of the issuer's units that can
 * build takes a build order on it at the footprint's middle, joined by the queue mode; another's unit is refused by
 * {@link Ownership}, a unit that does not build as unsupported, a full queue as such.
 */
final class Builders {
    private Builders() {}

    static Tally assign(
            CommandEnvelope envelope, MatchState state, UnitSet units, int building, Footprint at, QueueMode mode) {
        OrderQueues orders = state.orders();
        Tally tally = new Tally();
        double x = at.anchorX() + at.spanX() / 2.0;
        double z = at.anchorZ() + at.spanZ() / 2.0;
        for (int k = 0; k < units.size(); k++) {
            int unit = units.get(k);
            RejectReason reason = Ownership.check(envelope.issuer(), unit, state);
            if (reason != null) {
                tally.reject(reason);
                continue;
            }
            int i = Handle.index(unit);
            if (!state.stats().canBuild(i)) {
                tally.reject(RejectReason.UNSUPPORTED);
                continue;
            }
            int before = orders.size(i);
            boolean joined = switch (mode) {
                case REPLACE -> {
                    orders.replace(i, OrderKind.BUILD, x, z, 0, 0, building);
                    yield true;
                }
                case QUEUE -> orders.queue(i, OrderKind.BUILD, x, z, 0, 0, building);
                case PUSH_FRONT -> orders.pushFront(i, OrderKind.BUILD, x, z, 0, 0, building);
            };
            if (!joined) {
                tally.reject(RejectReason.QUEUE_FULL);
                continue;
            }
            if (mode != QueueMode.QUEUE || before == 0) {
                state.bus().publish(new OrderReceived(unit));
            }
            tally.accept();
        }
        return tally;
    }
}
