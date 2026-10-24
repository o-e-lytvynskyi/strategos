package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;

/**
 * Animal {@code handle} died this tick and left its carcass, meat node {@code node} (ECO-16): the mod keeps its body
 * lying where it fell, after the {@link UnitDied} of the same tick, until {@link CarcassGone}.
 */
@CoreApi
public record CarcassLeft(int handle, int node) implements MatchEvent {}
