package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;

/** Carcass {@code node} was gathered to the last (ECO-16): the mod removes the body it kept. */
@CoreApi
public record CarcassGone(int node) implements MatchEvent {}
