package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;

/** A unit's top-level state changed: the trace ORD-01.3 asks for. */
@CoreApi
public record UnitStateChanged(int handle, UnitState from, UnitState to) implements MatchEvent {}
