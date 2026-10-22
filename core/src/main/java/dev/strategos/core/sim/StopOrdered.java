package dev.strategos.core.sim;

import dev.strategos.core.sim.unitai.AiMessage;

/** A unit was told to stop. */
public record StopOrdered(int handle) implements BusMessage, AiMessage {}
