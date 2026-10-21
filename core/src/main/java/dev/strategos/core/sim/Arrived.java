package dev.strategos.core.sim;

/** A unit reached its move target. */
public record Arrived(int handle) implements BusMessage {}
