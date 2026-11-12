package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.sim.combat.ShotStore;
import org.jspecify.annotations.Nullable;

/**
 * A shot came down at its landing point this tick (CMB-04), on the unit {@code hit} or on nothing ({@code
 * Handle.NONE}), whether or not its shooter still lives. {@code from} is the release frozen at launch, null for a shot
 * saved before releases existed.
 */
@CoreApi
public record ShotLanded(
        int shooter,
        int target,
        int hit,
        ShotStore.@Nullable Release from,
        double toX,
        double toY,
        double toZ,
        long landTick)
        implements MatchEvent {}
