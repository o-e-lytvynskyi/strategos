package dev.strategos.core.sim;

import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.Construction;
import dev.strategos.core.sim.econ.Forage;
import dev.strategos.core.sim.prod.Production;

/**
 * P6, ECONOMY: bushes regrow (ECO-05), sites take their builders' work of the tick (BLD-03), and finished buildings
 * train (PROD-01, ECO-06); fields follow with their card.
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
                    Production.step(state, handle);
                }
            }
        }
    }
}
