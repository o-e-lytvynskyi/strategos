package dev.strategos.core.sim.command;

import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MatchSystem;
import dev.strategos.core.sim.Phase;
import java.util.ArrayDeque;

/**
 * P1: applies at most {@code rules().commandsPerTick()} pending commands from the head of the queue; the rest wait for
 * the next tick, in the snapshot. Every applied command adds exactly one {@code CommandResult} to the outputs.
 */
public final class CommandSystem implements MatchSystem {
    private final MatchState state;
    private final CommandRules rules;

    public CommandSystem(MatchState state, CommandRules rules) {
        this.state = state;
        this.rules = rules;
    }

    /** Queues a command, FIFO. */
    public void enqueue(CommandEnvelope envelope) {
        state.pendingCommands().addLast(envelope);
    }

    @Override
    public Phase phase() {
        return Phase.COMMANDS;
    }

    @Override
    public void run(MatchState state) {
        ArrayDeque<CommandEnvelope> pending = state.pendingCommands();
        int budget = state.config().rules().commandsPerTick();
        for (int applied = 0; applied < budget && !pending.isEmpty(); applied++) {
            state.outputs().add(rules.apply(pending.pollFirst(), state));
        }
    }
}
