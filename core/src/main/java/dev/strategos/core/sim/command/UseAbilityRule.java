package dev.strategos.core.sim.command;

import dev.strategos.core.content.AbilityDef;
import dev.strategos.core.protocol.CommandKind;
import dev.strategos.core.protocol.CommandKinds;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.Target;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.protocol.UseAbility;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.ability.AbilityRules;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.OrderQueues;

/**
 * UseAbility (UNIT-10, 04 §7.6): each owned unit whose template has the ability gets an ability order, joined to its
 * queue by the command's mode; the unit walks into range and fires it ({@code UsingAbility}). What the command checks,
 * at the tick boundary, is everything but the range: the unit has the ability ({@code UNSUPPORTED}), the target is the
 * kind the ability takes ({@code INVALID_ARGUMENT}) and is there ({@code UNKNOWN_TARGET}, {@code OUT_OF_BOUNDS}), the
 * ability is off cooldown ({@code ON_COOLDOWN}), its effects can land ({@code BLOCKED}) and the faction can pay
 * ({@code NOT_ENOUGH_RESOURCES}); the cost itself is paid at firing. An order that joins the queue is checked for cooldown and cost only when it fires.
 */
final class UseAbilityRule implements CommandRule<UseAbility> {
    @Override
    public CommandKind<UseAbility> kind() {
        return CommandKinds.USE_ABILITY;
    }

    @Override
    public CommandResult apply(CommandEnvelope envelope, UseAbility use, MatchState state) {
        UnitSet units = use.units();
        if (units.size() == 0) {
            return Tally.rejectAll(envelope, kind(), 0, RejectReason.INVALID_ARGUMENT);
        }
        String id = use.ability().value();
        int index = state.templates().abilityIndex(id);
        AbilityDef def = state.templates().ability(id);
        if (def == null) {
            return Tally.rejectAll(envelope, kind(), units.size(), RejectReason.UNSUPPORTED);
        }
        Target target = use.target();
        int targetUnit = target.kind() == Target.Kind.UNIT ? target.unit() : Handle.NONE;
        OrderQueues orders = state.orders();
        Tally tally = new Tally();
        for (int k = 0; k < units.size(); k++) {
            int handle = units.get(k);
            RejectReason reason = Ownership.check(envelope.issuer(), handle, state);
            if (reason == null) {
                int i = Handle.index(handle);
                reason = AbilityRules.defOf(state, i, id) == null
                        ? RejectReason.UNSUPPORTED
                        : AbilityRules.orderRefusal(state, handle, def, target, use.mode());
            }
            if (reason != null) {
                tally.reject(reason);
                continue;
            }
            int i = Handle.index(handle);
            double x = state.motion().x(i);
            double z = state.motion().z(i);
            if (target.kind() == Target.Kind.POINT) {
                x = target.x();
                z = target.z();
            } else if (targetUnit != Handle.NONE) {
                x = state.motion().x(Handle.index(targetUnit));
                z = state.motion().z(Handle.index(targetUnit));
            }
            int before = orders.size(i);
            boolean joined = switch (use.mode()) {
                case REPLACE -> {
                    orders.replace(i, OrderKind.ABILITY, x, z, 0, index, targetUnit);
                    yield true;
                }
                case QUEUE -> orders.queue(i, OrderKind.ABILITY, x, z, 0, index, targetUnit);
                case PUSH_FRONT -> orders.pushFront(i, OrderKind.ABILITY, x, z, 0, index, targetUnit);
            };
            if (!joined) {
                tally.reject(RejectReason.QUEUE_FULL);
                continue;
            }
            if (use.mode() != QueueMode.QUEUE || before == 0) {
                state.bus().publish(new OrderReceived(handle));
            }
            tally.accept();
        }
        return tally.result(envelope, kind());
    }
}
