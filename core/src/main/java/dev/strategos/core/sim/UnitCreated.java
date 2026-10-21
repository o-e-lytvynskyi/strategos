package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.FactionSlot;

/** A unit came into being; the mod materialises its body. */
@CoreApi
public record UnitCreated(int handle, int templateIndex, FactionSlot faction, double x, double y, double z, float yaw)
        implements MatchEvent {}
