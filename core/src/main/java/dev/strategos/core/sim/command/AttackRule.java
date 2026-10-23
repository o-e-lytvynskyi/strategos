package dev.strategos.core.sim.command;

import dev.strategos.core.content.Footprint;
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
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.combat.CombatSystem;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.OrderQueues;

/**
 * Attack (ORD-04): each owned unit with a melee attack gets the player's attack on the target, joined to its queue by
 * the command's mode, and chases it until it dies or the order ends. A target that is not a live unit is refused as
 * unknown, as a hidden one will be (04 §7.3); one of the issuer's own faction as an invalid argument; a unit with no
 * melee attack as unsupported. A building as the target is burned by the units with a torch (CMB-03).
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
        if (BuildingStore.isBuilding(target)) {
            return burn(envelope, attack, state);
        }
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

    /**
     * An attack on a building (CMB-03): each owned unit with a torch burns it ({@link OrderKind#BURN}), from a place
     * round its footprint; a unit without one is unsupported, the issuer's own building an invalid argument.
     */
    private CommandResult burn(CommandEnvelope envelope, Attack attack, MatchState state) {
        UnitSet units = attack.units();
        int target = attack.target();
        BuildingStore buildings = state.buildings();
        if (!buildings.alive(target)) {
            return Tally.rejectAll(envelope, kind(), units.size(), RejectReason.UNKNOWN_TARGET);
        }
        Footprint at = state.footprintOf(target);
        double x = at.anchorX() + at.spanX() / 2.0;
        double z = at.anchorZ() + at.spanZ() / 2.0;
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
            if (state.identity().faction(i) == buildings.faction(target)) {
                tally.reject(RejectReason.INVALID_ARGUMENT);
                continue;
            }
            int path = state.stats().path(CombatSystem.TORCH_FIRE);
            double fire = path < 0 ? Double.NaN : state.stats().read(i, path);
            if (!(fire > 0)) {
                tally.reject(RejectReason.UNSUPPORTED);
                continue;
            }
            int before = orders.size(i);
            boolean joined = switch (attack.mode()) {
                case REPLACE -> {
                    orders.replace(i, OrderKind.BURN, x, z, 0, 0, target);
                    yield true;
                }
                case QUEUE -> orders.queue(i, OrderKind.BURN, x, z, 0, 0, target);
                case PUSH_FRONT -> orders.pushFront(i, OrderKind.BURN, x, z, 0, 0, target);
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
