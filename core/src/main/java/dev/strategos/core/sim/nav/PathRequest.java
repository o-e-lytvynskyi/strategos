package dev.strategos.core.sim.nav;

/**
 * One search a unit waits for (04 §9.3): its id, the unit, the tick it was made, the surface it starts on, the goal on
 * the ground plane (match-local), the expansion cap, and the group fields PATH-09 uses (0 and false until then).
 */
public record PathRequest(
        int id, int handle, long tick, int fromNode, double goalX, double goalZ, int cap, int groupId, boolean join) {}
