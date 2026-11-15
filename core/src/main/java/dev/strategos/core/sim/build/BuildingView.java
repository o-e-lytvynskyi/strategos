package dev.strategos.core.sim.build;

import dev.strategos.core.content.Footprint;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.TemplateId;
import java.util.List;

/**
 * A copy of one building for cold readers (BLD-01): its handle, template, faction, where it stands, state, progress,
 * hit points and their most, the resources it takes in as a dropsite and the housing it gives once finished; its
 * queue (a unit's template net index, or for a technology -1 minus its own, the head first) with the head's progress in thousandths, and its rally point (its
 * kind, {@link BuildingStore#RALLY_NONE} for none, and where); a field's crop (ECO-09): what it holds in thousandths, -1
 * for a building that is no field, and the ticks it still ripens.
 */
@CoreApi
public record BuildingView(
        int handle,
        TemplateId template,
        int faction,
        Footprint footprint,
        int baseY,
        int state,
        long progress,
        double hitPoints,
        double maxHitPoints,
        List<String> dropsite,
        double housing,
        List<Integer> queue,
        long trained,
        int rallyKind,
        double rallyX,
        double rallyZ,
        long fieldAmount,
        long fieldRipening,
        boolean autoReseed,
        List<Integer> batches) {
    public BuildingView {
        dropsite = List.copyOf(dropsite);
        queue = List.copyOf(queue);
        batches = List.copyOf(batches);
    }
}
