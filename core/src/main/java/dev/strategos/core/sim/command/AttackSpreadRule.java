package dev.strategos.core.sim.command;

import dev.strategos.core.protocol.AttackSpread;
import dev.strategos.core.protocol.CommandKind;
import dev.strategos.core.protocol.CommandKinds;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.sim.spatial.UnitFilter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * AttackSpread (ORD-05): the enemies within the target's sight, nearest the target first (the lower index on a tie),
 * share the group's attackers, who are taken in the same order, one to a target in turn, so each enemy gets the
 * group's size over their number, rounded down or up. Each attacker gets an ordinary attack on its enemy, and all of
 * them one group id, which is how an attacker whose enemy dies finds the others' ({@code Spreading}). A target that is
 * not a live unit of another faction is refused, and so is one that is a building.
 */
final class AttackSpreadRule implements CommandRule<AttackSpread> {
    @Override
    public CommandKind<AttackSpread> kind() {
        return CommandKinds.ATTACK_SPREAD;
    }

    /** A unit and its distance to the target, for ordering. */
    private record Ranked(int index, double distance) {
        static final Comparator<Ranked> ORDER =
                Comparator.comparingDouble(Ranked::distance).thenComparingInt(Ranked::index);
    }

    @Override
    public CommandResult apply(CommandEnvelope envelope, AttackSpread spread, MatchState state) {
        UnitSet units = spread.units();
        if (units.size() == 0 || BuildingStore.isBuilding(spread.target())) {
            return Tally.rejectAll(envelope, kind(), units.size(), RejectReason.INVALID_ARGUMENT);
        }
        int target = spread.target();
        if (!state.entities().isAlive(target)) {
            return Tally.rejectAll(envelope, kind(), units.size(), RejectReason.UNKNOWN_TARGET);
        }
        int t = Handle.index(target);
        int targetFaction = state.identity().faction(t);
        double x = state.motion().x(t);
        double z = state.motion().z(t);
        Tally tally = new Tally();
        List<Ranked> attackers = new ArrayList<>();
        int attackerFaction = -1;
        for (int k = 0; k < units.size(); k++) {
            int handle = units.get(k);
            RejectReason reason = Ownership.check(envelope.issuer(), handle, state);
            if (reason == null) {
                int i = Handle.index(handle);
                reason = state.identity().faction(i) == targetFaction || targetFaction == state.gaia()
                        ? RejectReason.INVALID_ARGUMENT
                        : !state.stats().canAttack(i) ? RejectReason.UNSUPPORTED : null;
                if (reason == null) {
                    attackerFaction = attackerFaction < 0 ? state.identity().faction(i) : attackerFaction;
                    attackers.add(new Ranked(
                            i,
                            DetMath.length(
                                    state.motion().x(i) - x, state.motion().z(i) - z)));
                    continue;
                }
            }
            tally.reject(reason);
        }
        if (attackers.isEmpty()) {
            return tally.result(envelope, kind());
        }
        List<Ranked> enemies = new ArrayList<>();
        state.spatial()
                .forEachInRadius(
                        x,
                        z,
                        state.stats().vision(t),
                        UnitFilter.enemiesOf(attackerFaction, state.config().factions()),
                        j -> {
                            if (state.entities().aliveAt(j)) {
                                enemies.add(new Ranked(
                                        j,
                                        DetMath.length(
                                                state.motion().x(j) - x,
                                                state.motion().z(j) - z)));
                            }
                        });
        if (enemies.stream().noneMatch(e -> e.index() == t)) {
            enemies.add(new Ranked(t, 0)); // the target itself, though it stands outside its own sight or the grid
        }
        enemies.sort(Ranked.ORDER);
        attackers.sort(Ranked.ORDER);
        int group = state.newGroup();
        OrderQueues orders = state.orders();
        for (int m = 0; m < attackers.size(); m++) {
            int i = attackers.get(m).index();
            int enemy = enemies.get(m % enemies.size()).index();
            int to = state.entities().handleAt(enemy);
            double ex = state.motion().x(enemy);
            double ez = state.motion().z(enemy);
            int before = orders.size(i);
            boolean joined = switch (spread.mode()) {
                case REPLACE -> {
                    orders.replace(i, OrderKind.ATTACK, ex, ez, group, 0, to);
                    yield true;
                }
                case QUEUE -> orders.queue(i, OrderKind.ATTACK, ex, ez, group, 0, to);
                case PUSH_FRONT -> orders.pushFront(i, OrderKind.ATTACK, ex, ez, group, 0, to);
            };
            if (!joined) {
                tally.reject(RejectReason.QUEUE_FULL);
                continue;
            }
            if (spread.mode() != QueueMode.QUEUE || before == 0) {
                state.bus().publish(new OrderReceived(state.entities().handleAt(i)));
            }
            tally.accept();
        }
        return tally.result(envelope, kind());
    }
}
