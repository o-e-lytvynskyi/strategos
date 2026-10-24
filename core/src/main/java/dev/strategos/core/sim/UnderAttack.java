package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.FactionSlot;

/**
 * Faction {@code faction} is under attack at match-local ({@code x}, {@code z}) (UI-12): raised once for a sustained
 * attack, not once a blow; the mod tells that faction's players through {@code match_event}.
 */
@CoreApi
public record UnderAttack(FactionSlot faction, double x, double z) implements MatchEvent {}
