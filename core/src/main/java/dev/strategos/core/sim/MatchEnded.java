package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;

/**
 * The match is over (MATCH-01): at most one faction stands. {@code winner} is that faction, -1 when none does; {@code
 * ticks} how long it lasted.
 */
@CoreApi
public record MatchEnded(int winner, long ticks) implements MatchEvent {}
