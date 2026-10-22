package dev.strategos.core.sim;

import dev.strategos.core.sim.nav.PathStatus;
import dev.strategos.core.sim.unitai.AiMessage;

/**
 * A unit's path request was answered (PATH-03), decided at once or delivered at {@code t + k}: the unit now follows
 * that path, and {@code status} says how far it reaches. Spec §5.9 calls this message {@code PathResult}, which names
 * the search's own result.
 */
public record PathDelivered(int handle, PathStatus status) implements BusMessage, AiMessage {}
