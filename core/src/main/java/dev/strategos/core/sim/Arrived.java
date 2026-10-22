package dev.strategos.core.sim;

/** A unit's move is over: it reached its target, or the end of a path that gets no nearer ({@code NO_PATH}). */
public record Arrived(int handle) implements BusMessage {}
