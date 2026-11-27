package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;

/**
 * Unit {@code handle} reached rank {@code rank} (CMB-11, 1 and up): the mod shows it as the insignia over the body's
 * head. A body made later reads the unit's rank from the core when it is made, so only the change is an event.
 */
@CoreApi
public record UnitRanked(int handle, int rank) implements MatchEvent {}
