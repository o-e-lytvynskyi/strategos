package dev.strategos.core.sim.ai;

import dev.strategos.core.protocol.Command;

/** Where a computer player's commands go: the next tick's commands, under its own player slot. */
@FunctionalInterface
public interface CommandSink {
    void send(Command command);
}
