package dev.strategos.core.sim.econ;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.DetMath;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Where a match's nodes are, by resource and subtype and by the page of their anchor (ECO-02): the nearest node to a
 * point that a filter accepts (free slots, reachable, not exhausted), searched page ring by page ring outward. Built
 * from the store after discovery or a restore; nodes never move.
 */
@CoreApi
public final class NodeIndex {
    private final int pagesX;
    private final int pagesZ;
    private final int subtypes;
    private final List<int[]> buckets; // by (key * pages + page): node ids ascending

    public NodeIndex(NodeStore store, EconomyRules economy, int pagesX, int pagesZ) {
        this.pagesX = pagesX;
        this.pagesZ = pagesZ;
        int most = 1;
        for (ResourceDef r : economy.resources()) {
            most = Math.max(most, r.subtypes().size());
        }
        this.subtypes = most;
        int keys = Math.max(1, economy.size()) * most;
        List<List<Integer>> lists = new ArrayList<>(keys * pagesX * pagesZ);
        for (int i = 0; i < keys * pagesX * pagesZ; i++) {
            lists.add(new ArrayList<>(0));
        }
        for (int id = 1; id <= store.count(); id++) {
            int page = page(store.anchorX(id), store.anchorZ(id));
            int key = store.resource(id) * most + store.subtype(id);
            lists.get(key * pagesX * pagesZ + page).add(id);
        }
        this.buckets = new ArrayList<>(lists.size());
        for (List<Integer> l : lists) {
            buckets.add(l.stream().mapToInt(Integer::intValue).toArray());
        }
    }

    private int page(double x, double z) {
        int px = Math.max(0, Math.min(pagesX - 1, (int) Math.floor(x / 16)));
        int pz = Math.max(0, Math.min(pagesZ - 1, (int) Math.floor(z / 16)));
        return pz * pagesX + px;
    }

    /**
     * The node of {@code resource} and {@code subtype} nearest (x, z) by its anchor, within {@code maxDistance}, that
     * {@code accept} takes; the lower id on a tie; 0 for none.
     */
    public int nearest(
            NodeStore store, int resource, int subtype, double x, double z, double maxDistance, IntPredicate accept) {
        if (subtype >= subtypes) {
            return 0;
        }
        int key = resource * subtypes + subtype;
        int cx = Math.max(0, Math.min(pagesX - 1, (int) Math.floor(x / 16)));
        int cz = Math.max(0, Math.min(pagesZ - 1, (int) Math.floor(z / 16)));
        int best = 0;
        double bestD = Double.POSITIVE_INFINITY;
        int rings = (int) Math.ceil(maxDistance / 16) + 1;
        for (int r = 0; r <= rings; r++) {
            double ringNear = Math.max(0, (r - 1) * 16.0); // no node of ring r is nearer than this
            if (ringNear > bestD || ringNear > maxDistance) {
                break;
            }
            for (int pz = cz - r; pz <= cz + r; pz++) {
                for (int px = cx - r; px <= cx + r; px++) {
                    if (Math.max(Math.abs(px - cx), Math.abs(pz - cz)) != r
                            || px < 0
                            || pz < 0
                            || px >= pagesX
                            || pz >= pagesZ) {
                        continue;
                    }
                    for (int id : buckets.get(key * pagesX * pagesZ + pz * pagesX + px)) {
                        double d = DetMath.length(store.anchorX(id) - x, store.anchorZ(id) - z);
                        if (d <= maxDistance && (d < bestD || (d == bestD && id < best)) && accept.test(id)) {
                            best = id;
                            bestD = d;
                        }
                    }
                }
            }
        }
        return best;
    }
}
