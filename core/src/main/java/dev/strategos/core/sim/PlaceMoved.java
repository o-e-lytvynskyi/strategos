package dev.strategos.core.sim;

/**
 * Unit {@code handle}'s current order kept its kind but its place on the ground moved (CMB-02): an attacker following
 * its target. The unit asks for a path to the new place and its arrival latch lets go, as for a new order, but its
 * machine stays where it is.
 */
public record PlaceMoved(int handle) implements BusMessage {}
