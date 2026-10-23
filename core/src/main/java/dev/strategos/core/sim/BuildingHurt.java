package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;

/**
 * Building {@code handle} took {@code amount} hit points from {@code attacker} ({@code Handle.NONE} for none) this tick
 * (CMB-03): what an under-attack alert hears (UI-12).
 */
@CoreApi
public record BuildingHurt(int handle, int attacker, float amount) implements MatchEvent {}
