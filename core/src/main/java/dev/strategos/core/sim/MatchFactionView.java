package dev.strategos.core.sim;

import dev.strategos.core.content.FactionView;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.stats.ClassFilter;

/** A faction of a running match as {@link dev.strategos.core.content.Requirements} reads it (TECH-01). */
public final class MatchFactionView implements FactionView {
    private final MatchState state;
    private final int faction;

    public MatchFactionView(MatchState state, int faction) {
        this.state = state;
        this.faction = faction;
    }

    @Override
    public boolean researched(TechId tech) {
        return state.progress().researched(faction, tech);
    }

    @Override
    public boolean reached(String phase) {
        // Village is the phase of a faction with none researched (row 19); TECH-02 (T3) owns the others as the techs
        // strategos:phase_<name>. Names are bare and lower case, checked when the requirement compiles.
        if ("village".equals(phase)) {
            return true;
        }
        try {
            return researched(new TechId("strategos:phase_" + phase));
        } catch (IllegalArgumentException e) {
            return false; // a name the compiler would have refused: never reached
        }
    }

    @Override
    public int completeBuildings(String classFilter) {
        TemplateSet set = state.templates();
        ClassFilter filter = ClassFilter.compile(classFilter, set.tokens());
        BuildingStore b = state.buildings();
        int n = 0;
        for (int handle : b.handles()) {
            if (b.faction(handle) == faction
                    && b.state(handle) == BuildingStore.COMPLETE
                    && filter.matches(
                            set.tokens().bits(set.byIndex(b.template(handle)).classes()))) {
                n++;
            }
        }
        return n;
    }
}
