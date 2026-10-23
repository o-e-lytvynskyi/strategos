package dev.strategos.core.sim;

import dev.strategos.core.sim.econ.Forage;

/** P6, ECONOMY: bushes regrow (ECO-05); production, population and fields follow with their cards. */
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
    }
}
