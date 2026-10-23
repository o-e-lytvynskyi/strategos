package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.FactionSlot;

/**
 * A building of {@code faction} fell this tick (BLD-01: no hit points left) and is gone from the match; its blocks go
 * through the writer, and MATCH-01's victory reads the loss of a civic centre from it.
 */
@CoreApi
public record BuildingDestroyed(int handle, FactionSlot faction) implements MatchEvent {}
