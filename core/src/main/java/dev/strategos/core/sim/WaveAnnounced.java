package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.FactionSlot;

/**
 * Faction {@code faction}'s computer player announced a wave (AI-17): it musters at match-local ({@code x}, {@code z})
 * and leaves at tick {@code leaves}; the mod tells every other player, with a horn.
 */
@CoreApi
public record WaveAnnounced(FactionSlot faction, int x, int z, long leaves) implements MatchEvent {}
