package dev.strategos.core.sim.build;

import dev.strategos.core.content.Footprint;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.TemplateId;
import java.util.List;

/**
 * A copy of one building for cold readers (BLD-01): its handle, template, faction, where it stands, state, progress,
 * hit points and their most, the resources it takes in as a dropsite and the housing it gives once finished.
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
        double housing) {
    public BuildingView {
        dropsite = List.copyOf(dropsite);
    }
}
