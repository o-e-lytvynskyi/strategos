package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;

/** A change in the match the mod reacts to: a unit created, hurt or dead, or a unit's state changed. */
@CoreApi
public sealed interface MatchEvent extends OutputEvent permits UnitCreated, UnitStateChanged, UnitHurt, UnitDied {}
