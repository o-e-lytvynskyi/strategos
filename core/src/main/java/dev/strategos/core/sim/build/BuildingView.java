package dev.strategos.core.sim.build;

import dev.strategos.core.content.Footprint;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.TemplateId;
import java.util.List;

/**
 * A copy of one building for cold readers (BLD-01): its handle, template, faction, where it stands, state, progress,
 * hit points and their most, the resources it takes in as a dropsite and the housing it gives once finished; its
 * queue (templates' net indices, the head first) with the head's progress in thousandths, and its rally point (its
 * kind, {@link BuildingStore#RALLY_NONE} for none, and where).
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
        double rallyZ) {
    public BuildingView {
        dropsite = List.copyOf(dropsite);
        queue = List.copyOf(queue);
    }
}
