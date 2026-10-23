package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.FactionSlot;

/**
 * A unit of {@code faction} died this tick, last hit by {@code attacker} ({@code Handle.NONE} for a vanilla source),
 * and is gone from the match (CMB-01); the mod plays its body's death and removes it.
 */
@CoreApi
public record UnitDied(int handle, FactionSlot faction, int attacker) implements MatchEvent {}
