package dev.strategos.core.sim;

import dev.strategos.core.sim.prod.Population;
import dev.strategos.core.sim.stats.StatService;

/** What a unit takes of its faction's population: its {@code Cost/population}, 0 for none (ECO-06). */
final class PopulationCost {
    private PopulationCost() {}

    static int ofUnit(StatService stats, int unit) {
        int path = stats.path(Population.COST);
        return round(path < 0 ? Double.NaN : stats.read(unit, path));
    }

    static int ofTemplate(StatService stats, int template, int faction) {
        int path = stats.path(Population.COST);
        return round(path < 0 ? Double.NaN : stats.blockValue(template, faction, path));
    }

    private static int round(double v) {
        return Double.isFinite(v) && v > 0 ? (int) Math.round(v) : 0;
    }
}
