package dev.strategos.core.sim;

import dev.strategos.core.sim.unitai.AiMessage;

/** A unit's move is over: it reached its target, or the end of a path that gets no nearer ({@code NO_PATH}). */
public record Arrived(int handle) implements BusMessage, AiMessage {}
