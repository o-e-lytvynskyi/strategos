package dev.strategos.core.sim.civ;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.sim.MatchState;

/**
 * What a faction's economy does of its own each economy step (ADR-0028), called once per faction at the end of the step,
 * in faction order. The default does nothing. A variant that adds resources goes through the treasury's {@code grant},
 * so the conservation invariant holds; one that needs state of its own declares a store.
 */
@CoreApi
@FunctionalInterface
public interface UpkeepRule {
    UpkeepRule DEFAULT = (s, faction) -> {};

    void step(MatchState s, int faction);
}
