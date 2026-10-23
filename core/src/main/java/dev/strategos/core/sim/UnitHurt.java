package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;

/**
 * A unit took {@code amount} damage from {@code attacker} ({@code Handle.NONE} for a vanilla source) this tick
 * (CMB-01); the mod flashes its body red for those who track it.
 */
@CoreApi
public record UnitHurt(int handle, int attacker, float amount) implements MatchEvent {}
