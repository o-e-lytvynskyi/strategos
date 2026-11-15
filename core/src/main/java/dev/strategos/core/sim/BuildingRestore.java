package dev.strategos.core.sim;

import dev.strategos.core.content.TechDef;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.Placing;
import java.util.List;

/** A snapshot's buildings put back (MATCH-04): their footprints are blocked again. */
final class BuildingRestore {
    private BuildingRestore() {}

    /** Restores {@code saved}; the writer's blocks are the world's already. */
    static void restore(MatchState state, List<BuildingStore.Saved> saved) {
        BuildingStore buildings = state.buildings();
        buildings.restore(saved, id -> {
            TechDef tech = state.templates().tech(new TechId(id));
            return tech == null ? -1 : tech.netIndex();
        });
        for (int handle : buildings.handles()) {
            if (buildings.state(handle) != BuildingStore.FOUNDATION
                    && Placing.obstructs(
                            state, buildings.template(handle), buildings.faction(handle))) { // a field's are walked on
                for (int cell : state.footprintOf(handle).columns(state.blueprintOf(handle))) {
                    state.restoreColumn(cell >>> 16, cell & 0xFFFF);
                }
            }
        }
    }
}
