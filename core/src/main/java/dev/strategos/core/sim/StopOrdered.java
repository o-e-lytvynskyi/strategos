package dev.strategos.core.sim;

/** A unit was told to stop. */
public record StopOrdered(int handle) implements BusMessage {}
