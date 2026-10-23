package dev.strategos.core.sim.command;

import dev.strategos.core.protocol.CommandKind;
import dev.strategos.core.protocol.CommandKinds;
import dev.strategos.core.protocol.Follow;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.OrderQueues;

/**
 * Follow (CAM-07): each owned unit follows the issuer, joined to its queue by the command's mode; the units of one
 * command share a group and each takes the next place behind the leader, in handle order. Only a player leads: the
 * administrator's follow is refused. Until the leader stands in first person a follower stays where it is.
 */
final class FollowRule implements CommandRule<Follow> {
    @Override
    public CommandKind<Follow> kind() {
        return CommandKinds.FOLLOW;
    }

    @Override
    public CommandResult apply(CommandEnvelope envelope, Follow follow, MatchState state) {
        UnitSet units = follow.units();
        if (units.size() == 0) {
            return Tally.rejectAll(envelope, kind(), 0, RejectReason.INVALID_ARGUMENT);
        }
        if (envelope.issuer().isAdmin()) {
            return Tally.rejectAll(envelope, kind(), units.size(), RejectReason.NOT_PERMITTED);
        }
        int leader = envelope.issuer().value();
        OrderQueues orders = state.orders();
        Tally tally = new Tally();
        int[] taking = new int[units.size()];
        int n = 0;
        for (int k = 0; k < units.size(); k++) {
            RejectReason reason = Ownership.check(envelope.issuer(), units.get(k), state);
            if (reason != null) {
                tally.reject(reason);
            } else {
                taking[n++] = units.get(k);
            }
        }
        int group = n > 1 ? state.newGroup() : 0;
        for (int k = 0; k < n; k++) {
            int handle = taking[k];
            int i = Handle.index(handle);
            double x =
                    state.leads(leader) ? state.leaderX(leader) : state.motion().x(i);
            double z =
                    state.leads(leader) ? state.leaderZ(leader) : state.motion().z(i);
            int before = orders.size(i);
            boolean joined = switch (follow.mode()) {
                case REPLACE -> {
                    orders.replace(i, OrderKind.FOLLOW, x, z, group, k, leader);
                    yield true;
                }
                case QUEUE -> orders.queue(i, OrderKind.FOLLOW, x, z, group, k, leader);
                case PUSH_FRONT -> orders.pushFront(i, OrderKind.FOLLOW, x, z, group, k, leader);
            };
            if (!joined) {
                tally.reject(RejectReason.QUEUE_FULL);
                continue;
            }
            if (follow.mode() != QueueMode.QUEUE || before == 0) {
                state.bus().publish(new OrderReceived(handle));
            }
            tally.accept();
        }
        return tally.result(envelope, kind());
    }
}
