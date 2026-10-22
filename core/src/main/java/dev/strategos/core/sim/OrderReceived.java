package dev.strategos.core.sim;

import dev.strategos.core.sim.unitai.AiMessage;

/** A unit took a new order. */
public record OrderReceived(int handle) implements BusMessage, AiMessage {}
