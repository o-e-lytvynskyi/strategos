package dev.strategos.core.sim.combat;

import dev.strategos.core.sim.MatchState;

/** A test's way to {@link TownArrows}' package-private seam: the units it counts inside a building. */
public final class TownArrowsAccess {
    private TownArrowsAccess() {}

    public static int garrisoned(MatchState s, int building) {
        return TownArrows.garrisoned(s, building);
    }
}
