package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;

/**
 * A ranged shot left {@code shooter} for {@code target} this tick (CMB-04): from the frozen release point to the aimed
 * landing point, flying until {@code landTick}. The mod shows its launch; the impact is {@link ShotLanded}.
 */
@CoreApi
public record ShotFired(
        int shooter,
        int target,
        double fromX,
        double fromY,
        double fromZ,
        double toX,
        double toY,
        double toZ,
        long launchTick,
        long landTick)
        implements MatchEvent {}
