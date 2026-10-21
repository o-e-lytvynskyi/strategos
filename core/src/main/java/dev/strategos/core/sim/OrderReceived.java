package dev.strategos.core.sim;

/** A unit took a new order. */
public record OrderReceived(int handle) implements BusMessage {}
