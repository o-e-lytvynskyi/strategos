package dev.strategos.core.sim.civ;

import dev.strategos.core.content.TechDef;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.prod.Techs;

/**
 * A faction's phase (ADR-0028), the only source of it: {@link Techs#phase}, the requirements' "reached a phase"
 * ({@code MatchFactionView}) and what replication tells the HUD all ask this rule. The default is the highest phase
 * ordinal the faction has researched.
 */
@CoreApi
public interface PhaseRule {
    PhaseRule DEFAULT = new PhaseRule() {
        @Override
        public int phase(MatchState s, int faction) {
            return Techs.highestResearchedPhase(s, faction);
        }

        @Override
        public boolean reached(MatchState s, int faction, String name) {
            return s.progress().researched(faction, new TechId("strategos:phase_" + name)); // today's answer
        }
    };

    /** {@code faction}'s phase ordinal, 0 (Village) the first. */
    int phase(MatchState s, int faction);

    /**
     * Whether {@code faction} has reached the phase named {@code name} (bare, lower case: {@code town}); Village always.
     * By default its phase ordinal is at least the named phase's.
     */
    default boolean reached(MatchState s, int faction, String name) {
        TechDef tech = s.templates().tech(new TechId("strategos:phase_" + name));
        return tech != null && tech.isPhase() && phase(s, faction) >= tech.ordinal();
    }
}
