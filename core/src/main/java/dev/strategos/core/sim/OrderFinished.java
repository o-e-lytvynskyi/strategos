package dev.strategos.core.sim;

import dev.strategos.core.sim.unitai.AiMessage;

/**
 * A unit's current order is over, done or {@code failed} (ADR-0014's {@code finishOrder}); rally chains (ORD-02) and
 * the unit's machine may react to it.
 */
public record OrderFinished(int handle, boolean failed) implements BusMessage, AiMessage {}
