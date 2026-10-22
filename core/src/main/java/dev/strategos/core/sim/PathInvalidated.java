package dev.strategos.core.sim;

import dev.strategos.core.sim.unitai.AiMessage;

/**
 * A unit's path no longer holds (PATH-15): a page republished with another slice crosses its route, a path searched
 * before such a change cannot be walked on the grid it arrives on, or avoidance pushed the unit where its line of walk
 * to its aim is lost (PATH-06). A walking unit asks again once allowed.
 */
public record PathInvalidated(int handle) implements BusMessage, AiMessage {}
