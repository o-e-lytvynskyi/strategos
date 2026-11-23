package dev.strategos.core.sim.garrison;

import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MatchSystem;
import dev.strategos.core.sim.Phase;
import dev.strategos.core.sim.build.BuildingStore;

/**
 * What a garrison does each tick (BLD-10, M9 spec §2.7), in ECONOMY after the tick's blows: a building that has fallen
 * under its {@code GarrisonHolder/ejectBelow} share of its most hit points lets everyone out, and every {@link
 * #HEAL_PERIOD} ticks each occupant of a building with {@code GarrisonHolder/heal} regains that many hit points, never
 * above its most. A match nobody garrisons in does no work here.
 */
public final class GarrisonSystem implements MatchSystem {
    /** The ticks between two heals: the heal rate is per second. */
    public static final int HEAL_PERIOD = 20;

    @Override
    public Phase phase() {
        return Phase.ECONOMY;
    }

    @Override
    public void run(MatchState s) {
        if (s.garrison().empty()) {
            return;
        }
        boolean heal = s.tick() % HEAL_PERIOD == 0;
        for (int building : s.garrison().holders()) {
            if (!s.buildings().alive(building)) {
                continue;
            }
            double below = Garrisons.ejectBelow(s, building);
            if (below > 0 && s.buildings().state(building) == BuildingStore.COMPLETE) {
                double max = s.buildingMaxHp(building);
                if ((max - s.buildings().damage(building)) / max < below) {
                    Garrisons.ejectAll(s, building);
                    continue;
                }
            }
            double rate = heal ? Garrisons.heal(s, building) : 0;
            if (rate > 0) {
                for (int unit : s.garrison().occupants(building)) {
                    int i = Handle.index(unit);
                    s.health().setDamage(i, Math.max(0, s.health().damage(i) - rate));
                }
            }
        }
    }
}
