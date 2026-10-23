package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;

/**
 * Unit {@code attacker} struck at unit {@code target} this tick (CMB-02): the mod swings the attacker's body. The hit
 * itself is the target's {@link UnitHurt}, in the same tick's COMBAT phase.
 */
@CoreApi
public record UnitStruck(int attacker, int target) implements MatchEvent {}
