package dev.strategos.core.sim.command;

import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.CommandKind;
import dev.strategos.core.protocol.CommandKinds;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchState;
import java.util.ArrayList;
import java.util.List;

/** One rule per command kind. */
public final class CommandRules {
    private final List<CommandRule<?>> rules = new ArrayList<>();

    /** The match's rules: M0's Move, Stop and SpawnUnits, and M2's Attack and AttackMove (ORD-04) and Follow (CAM-07). */
    public static CommandRules m0() {
        return new CommandRules()
                .register(new MoveRule())
                .register(new StopRule())
                .register(new SpawnUnitsRule())
                .register(new AttackRule())
                .register(new AttackMoveRule())
                .register(new FollowRule())
                .register(new GrantRule())
                .register(new SpawnBuildingRule())
                .register(new GatherRule())
                .register(new PlaceBuildingRule());
    }

    /** A second rule for a kind throws. */
    public CommandRules register(CommandRule<?> rule) {
        for (CommandRule<?> existing : rules) {
            if (existing.kind().id().equals(rule.kind().id())) {
                throw new IllegalArgumentException(
                        "a second rule for " + rule.kind().id());
            }
        }
        rules.add(rule);
        return this;
    }

    public CommandResult apply(CommandEnvelope envelope, MatchState state) {
        for (CommandRule<?> rule : rules) {
            if (rule.kind().type() == envelope.command().getClass()) {
                return applyTyped(rule, envelope, state);
            }
        }
        throw new IllegalStateException(
                "no rule for " + envelope.command().getClass().getName());
    }

    /** Every kind of the catalogue has exactly one rule, and no rule serves a kind outside it; checked at creation. */
    public void verifyCovers(CommandKinds kinds) {
        for (CommandKind<?> kind : kinds.all()) {
            long count =
                    rules.stream().filter(r -> r.kind().id().equals(kind.id())).count();
            if (count != 1) {
                throw new IllegalStateException(count + " rules for command kind " + kind.id());
            }
        }
        for (CommandRule<?> rule : rules) {
            if (kinds.byId(rule.kind().id()) == null) {
                throw new IllegalStateException("a rule for " + rule.kind().id() + ", which the catalogue lacks");
            }
        }
    }

    private static <C extends Command> CommandResult applyTyped(
            CommandRule<C> rule, CommandEnvelope envelope, MatchState state) {
        return rule.apply(envelope, rule.kind().type().cast(envelope.command()), state);
    }
}
