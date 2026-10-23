package dev.strategos.core.sim.command;

import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.CommandKind;
import dev.strategos.core.protocol.CommandKinds;
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
 * Attack (ORD-04): each owned unit with a melee attack gets the player's attack on the target, joined to its queue by
 * the command's mode, and chases it until it dies or the order ends. A target that is not a live unit is refused as
 * unknown, as a hidden one will be (04 §7.3); one of the issuer's own faction as an invalid argument; a unit with no
 * melee attack as unsupported.
 */
final class AttackRule implements CommandRule<Attack> {
    @Override
    public CommandKind<Attack> kind() {
        return CommandKinds.ATTACK;
    }

    @Override
    public CommandResult apply(CommandEnvelope envelope, Attack attack, MatchState state) {
        UnitSet units = attack.units();
        if (units.size() == 0) {
            return Tally.rejectAll(envelope, kind(), 0, RejectReason.INVALID_ARGUMENT);
        }
        int target = attack.target();
        if (!state.entities().isAlive(target)) {
            return Tally.rejectAll(envelope, kind(), units.size(), RejectReason.UNKNOWN_TARGET);
        }
        int t = Handle.index(target);
        double x = state.motion().x(t);
        double z = state.motion().z(t);
        int targetFaction = state.identity().faction(t);
        OrderQueues orders = state.orders();
        Tally tally = new Tally();
        for (int k = 0; k < units.size(); k++) {
            int handle = units.get(k);
            RejectReason reason = Ownership.check(envelope.issuer(), handle, state);
            if (reason != null) {
                tally.reject(reason);
                continue;
            }
            int i = Handle.index(handle);
            if (state.identity().faction(i) == targetFaction) {
                tally.reject(RejectReason.INVALID_ARGUMENT);
                continue;
            }
            if (!state.stats().canMelee(i)) {
                tally.reject(RejectReason.UNSUPPORTED);
                continue;
            }
            int before = orders.size(i);
            boolean joined = switch (attack.mode()) {
                case REPLACE -> {
                    orders.replace(i, OrderKind.ATTACK, x, z, 0, 0, target);
                    yield true;
                }
                case QUEUE -> orders.queue(i, OrderKind.ATTACK, x, z, 0, 0, target);
                case PUSH_FRONT -> orders.pushFront(i, OrderKind.ATTACK, x, z, 0, 0, target);
            };
            if (!joined) {
                tally.reject(RejectReason.QUEUE_FULL);
                continue;
            }
            if (attack.mode() != QueueMode.QUEUE || before == 0) {
                state.bus().publish(new OrderReceived(handle));
            }
            tally.accept();
        }
        return tally.result(envelope, kind());
    }
}
