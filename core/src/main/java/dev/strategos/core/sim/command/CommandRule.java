package dev.strategos.core.sim.command;

import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.CommandKind;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchState;

/** Validates and applies one kind of command in a single pass. */
public interface CommandRule<C extends Command> {
    CommandKind<C> kind();

    CommandResult apply(CommandEnvelope envelope, C command, MatchState state);
}
