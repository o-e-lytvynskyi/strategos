package dev.strategos.core.sim.command;

import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.CommandKind;
import dev.strategos.core.protocol.CommandKinds;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.SlotPlanner;

/**
 * AttackMove (ORD-04): as a move, each owned unit to its own place round the target, but walking and fighting: an
 * enemy met in sight is engaged, then the walk goes on. A unit without a melee attack just walks.
 */
final class AttackMoveRule implements CommandRule<AttackMove> {
    private final SlotPlanner slots = new SlotPlanner();

    @Override
    public CommandKind<AttackMove> kind() {
        return CommandKinds.ATTACK_MOVE;
    }

    @Override
    public CommandResult apply(CommandEnvelope envelope, AttackMove move, MatchState state) {
        return MoveRule.walk(
                envelope, kind(), OrderKind.ATTACK_MOVE, move.units(), move.target(), move.mode(), 0, state, slots);
    }
}
