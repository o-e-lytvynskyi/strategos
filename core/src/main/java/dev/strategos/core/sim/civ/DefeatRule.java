package dev.strategos.core.sim.civ;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.build.BuildingStore;

/**
 * Whether a faction still has what keeps it in play (ADR-0028), read under {@code Victory.CIVIC_CENTRE}; the per-faction
 * latch (a faction that never held it is never defeated by losing it) stays in {@code Outcome}. The default: a finished
 * building of its civilisation's {@code centreClass}; a foundation or a site counts as none.
 */
@CoreApi
@FunctionalInterface
public interface DefeatRule {
    DefeatRule DEFAULT = DefeatRule::holdsCentre;

    boolean holds(MatchState s, int faction);

    private static boolean holdsCentre(MatchState s, int faction) {
        String centre = s.civs().centreClass(faction);
        BuildingStore b = s.buildings();
        for (int k = 0; k < b.capacity(); k++) { // no array per faction per tick
            int handle = b.handleAt(k);
            if (handle != Handle.NONE
                    && b.faction(handle) == faction
                    && b.state(handle) == BuildingStore.COMPLETE
                    && s.templates().byIndex(b.template(handle)).classes().contains(centre)) {
                return true;
            }
        }
        return false;
    }
}
