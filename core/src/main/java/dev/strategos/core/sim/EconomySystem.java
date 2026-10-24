package dev.strategos.core.sim;

import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.Construction;
import dev.strategos.core.sim.econ.Fields;
import dev.strategos.core.sim.econ.Forage;
import dev.strategos.core.sim.prod.Production;

/**
 * P6, ECONOMY: bushes regrow (ECO-05), sites take their builders' work of the tick (BLD-03), and finished buildings
 * train (PROD-01, ECO-06); fields ripen (ECO-09).
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
            Fields.ripen(state);
            Fields.autoReseed(state);
            for (int handle : buildings.handles()) {
                if (buildings.state(handle) == BuildingStore.CONSTRUCTING) {
                    Construction.step(state, handle);
                } else if (buildings.state(handle) == BuildingStore.COMPLETE && buildings.damage(handle) > 0) {
                    Construction.repair(state, handle);
                    Production.step(state, handle);
                } else {
                    buildings.clearTick(handle);
                    Production.step(state, handle);
                }
            }
        }
    }
}
