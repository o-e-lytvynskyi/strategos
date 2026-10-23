package dev.strategos.core.sim;

import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.Construction;
import dev.strategos.core.sim.econ.Forage;

/**
 * P6, ECONOMY: bushes regrow (ECO-05) and sites take their builders' work of the tick (BLD-03); production, population
 * and fields follow with their cards.
 */
final class EconomySystem implements MatchSystem {
    @Override
    public Phase phase() {
        return Phase.ECONOMY;
    }

    @Override
    public void run(MatchState state) {
        if (state.nodes().count() > 0) {
            Forage.regrow(state);
        }
        BuildingStore buildings = state.buildings();
        if (!buildings.empty()) {
            for (int handle : buildings.handles()) {
                if (buildings.state(handle) == BuildingStore.CONSTRUCTING) {
                    Construction.step(state, handle);
                } else {
                    buildings.clearTick(handle);
                }
            }
        }
    }
}
