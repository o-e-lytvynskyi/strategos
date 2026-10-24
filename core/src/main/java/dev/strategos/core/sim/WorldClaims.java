package dev.strategos.core.sim;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.DamageStages;
import dev.strategos.core.sim.econ.NodeStore;
import dev.strategos.core.sim.nav.MaterialKind;
import dev.strategos.core.sim.world.WritePriority;
import java.util.ArrayList;
import java.util.List;

/**
 * The blocks the core owns, as they must stand (MATCH-04, ADR-0018), for the mod's reconciler after a resume: each
 * building's, as built so far and less what its damage stage took down, owned by it; and each block a felled tree or a
 * quarried rock gave up, as air. Everything else in the world is the world's, which the terrain capture brings in.
 */
public final class WorldClaims {
    private WorldClaims() {}

    public static List<WorldEffect> of(MatchState s) {
        List<WorldEffect> out = new ArrayList<>();
        BuildingStore b = s.buildings();
        for (int handle : b.handles()) {
            Blueprint bp = s.templates().blueprint(b.template(handle));
            if (bp == null) {
                continue;
            }
            Footprint at = s.footprintOf(handle);
            int baseY = b.baseY(handle);
            int site = Handle.index(handle);
            int built = b.state(handle) == BuildingStore.COMPLETE ? bp.count() : Math.min(bp.count(), b.placed(handle));
            boolean[] fallen = new boolean[bp.count()];
            if (b.state(handle) == BuildingStore.COMPLETE && b.stage(handle) > 0) {
                int[] ranks = s.damageRanks(bp);
                int n = DamageStages.fallen(
                        b.stage(handle),
                        ranks.length,
                        threshold(s, handle, 1),
                        threshold(s, handle, 2),
                        threshold(s, handle, 3));
                for (int r = 0; r < n; r++) {
                    fallen[ranks[r]] = true;
                }
            }
            for (int k = 0; k < built; k++) {
                out.add(new WorldEffect(
                        site,
                        WritePriority.CONSTRUCTION,
                        at.worldX(bp.x(k), bp.z(k)),
                        baseY + bp.y(k),
                        at.worldZ(bp.x(k), bp.z(k)),
                        fallen[k] ? WorldEffect.AIR : bp.palette().get(bp.state(k)),
                        fallen[k] ? 0 : handle));
            }
        }
        NodeStore nodes = s.nodes();
        for (int id = 1; id <= nodes.count(); id++) {
            long[] blocks = nodes.blocks(id);
            MaterialKind kind = nodes.kind(id);
            if (kind != MaterialKind.LOG
                    && kind != MaterialKind.LEAVES
                    && kind != MaterialKind.STONE
                    && kind != MaterialKind.ORE) {
                continue; // only felling and quarrying take blocks away; a bush, a crop, a carcass or a deposit keeps
                // its own
            }
            for (int k = 0; k < nodes.gone(id) && k < blocks.length; k++) {
                out.add(new WorldEffect(
                        id,
                        WritePriority.NODE_REMOVAL,
                        NodeStore.x(blocks[k]),
                        NodeStore.y(blocks[k]),
                        NodeStore.z(blocks[k]),
                        WorldEffect.AIR,
                        0));
            }
        }
        return out;
    }

    private static double threshold(MatchState s, int handle, int k) {
        return DamageStages.threshold(s, handle, k);
    }
}
