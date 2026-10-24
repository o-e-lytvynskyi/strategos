package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;

/**
 * A change in the match the mod reacts to: a unit created, striking, hurt or dead, a unit's state or its gear changed,
 * a building hurt or destroyed, a block's crack, a torch's flames, a faction defeated, the match's end.
 */
@CoreApi
public sealed interface MatchEvent extends OutputEvent
        permits UnitCreated,
                UnitStateChanged,
                UnitStruck,
                UnitHurt,
                UnitDied,
                BuildingDestroyed,
                BuildingHurt,
                UnitGear,
                BlockCracked,
                FlameShown,
                FactionDefeated,
                MatchEnded {}
