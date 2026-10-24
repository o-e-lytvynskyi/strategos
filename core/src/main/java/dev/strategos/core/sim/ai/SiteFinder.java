package dev.strategos.core.sim.ai;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.econ.NodeView;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A computer player's building sites (AI-02, 04 §14), found rather than scripted: the corners within {@value #REACH}
 * blocks of its centre, nearest a wanted point first (its centre, or what a step wants it near), {@value #GAP} blocks
 * clear of other buildings, that the ground's placement rules take, and that wall nothing off: with the building there,
 * every node and every own unit the centre's ground reached before must still be reached, walking the region's floors
 * a block up or down at a time round the footprints. A pure function of the match in a think; its search is bounded,
 * each look at a site spending the budget.
 */
final class SiteFinder {
    /** How far from the centre a site's middle may lie. */
    static final int REACH = 40;

    /** The most sites looked at in one search. */
    static final int LOOKS = 48;

    /** The region the reachability walk covers round the centre, in blocks each way. */
    static final int REGION = 56;

    /**
     * The free ground a site keeps round it from other buildings' footprints, in blocks: two units pass in it, so no
     * one-block lane between buildings jams with a worker standing in it.
     */
    static final int GAP = 2;

    private SiteFinder() {}

    /**
     * The corner of the best site for {@code building} near ({@code wantX}, {@code wantZ}), skipping corner
     * {@code skip} (a packed corner the server just refused, or -1), or null when none within reach passes.
     */
    static int @Nullable [] find(
            AiView view, AiBudget budget, Base base, TemplateId building, double wantX, double wantZ, long skip) {
        UnitTemplate t = view.templates().byId(building);
        Blueprint bp = t == null ? null : view.templates().blueprint(t.netIndex());
        if (bp == null) {
            return null;
        }
        Footprint probe = Footprint.of(bp, 0, 0, 0);
        List<int[]> candidates = new ArrayList<>();
        int cx = (int) Math.floor(base.x());
        int cz = (int) Math.floor(base.z());
        Footprint seed = base.centre().footprint();
        for (int dx = -REACH; dx <= REACH; dx++) {
            for (int dz = -REACH; dz <= REACH; dz++) {
                int mx = cx + dx;
                int mz = cz + dz;
                if (DetMath.length(dx, dz) > REACH) {
                    continue;
                }
                int x = mx - probe.spanX() / 2;
                int z = mz - probe.spanZ() / 2;
                if (x < 0
                        || z < 0
                        || x + probe.spanX() > view.bounds().sizeX()
                        || z + probe.spanZ() > view.bounds().sizeZ()) {
                    continue;
                }
                double d = DetMath.length(mx + 0.5 - wantX, mz + 0.5 - wantZ);
                candidates.add(new int[] {x, z, (int) Math.round(d * 16)});
            }
        }
        candidates.sort((a, b) -> a[2] != b[2]
                ? Integer.compare(a[2], b[2])
                : a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
        Reach before = null;
        int looks = 0;
        for (int[] c : candidates) {
            if (looks == LOOKS || budget.exhausted()) {
                return null;
            }
            if (pack(c[0], c[1]) == skip) {
                continue;
            }
            if (crowded(view, c[0], c[1], probe) || view.placementRefusal(building, c[0], c[1]) != null) {
                continue;
            }
            looks++;
            if (before == null) {
                before = Reach.walk(view, cx, cz, seed, null);
            }
            budget.spend(10);
            Reach after = Reach.walk(view, cx, cz, seed, Footprint.of(bp, c[0], c[1], 0));
            if (after.covers(before)) {
                return new int[] {c[0], c[1]};
            }
        }
        return null;
    }

    /** Whether another building's footprint lies within {@value #GAP} blocks of a site at corner (x, z). */
    private static boolean crowded(AiView view, int x, int z, Footprint probe) {
        for (int cx = x - GAP; cx < x + probe.spanX() + GAP; cx++) {
            for (int cz = z - GAP; cz < z + probe.spanZ() + GAP; cz++) {
                boolean ring = cx < x || cz < z || cx >= x + probe.spanX() || cz >= z + probe.spanZ();
                boolean inside = cx >= 0
                        && cz >= 0
                        && cx < view.bounds().sizeX()
                        && cz < view.bounds().sizeZ();
                if (ring && inside && view.footprintAt(cx, cz)) {
                    return true;
                }
            }
        }
        return false;
    }

    static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /** What the seed's ground reaches in the region: the nodes and the own units by index in the view's lists. */
    record Reach(BitSet nodes, BitSet units) {
        boolean covers(Reach other) {
            BitSet lostNodes = (BitSet) other.nodes.clone();
            lostNodes.andNot(nodes);
            BitSet lostUnits = (BitSet) other.units.clone();
            lostUnits.andNot(units);
            return lostNodes.isEmpty() && lostUnits.isEmpty();
        }

        /**
         * A walk in the region round (ox, oz) from the ground round footprint {@code seed}, {@code extra} a footprint
         * counted as a building's, or none.
         */
        static Reach walk(AiView view, int ox, int oz, Footprint seed, @Nullable Footprint extra) {
            int x0 = Math.max(0, ox - REGION);
            int z0 = Math.max(0, oz - REGION);
            int x1 = Math.min(view.bounds().sizeX() - 1, ox + REGION);
            int z1 = Math.min(view.bounds().sizeZ() - 1, oz + REGION);
            int w = x1 - x0 + 1;
            int h = z1 - z0 + 1;
            double[] floor = new double[w * h];
            boolean[] open = new boolean[w * h];
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    int k = (x - x0) * h + (z - z0);
                    floor[k] = view.floorAt(x, z);
                    open[k] = !Double.isNaN(floor[k]) && !view.footprintAt(x, z) && !inside(extra, x, z);
                }
            }
            boolean[] seen = new boolean[w * h];
            int[] queue = new int[w * h];
            int head = 0;
            int tail = 0;
            for (int x = seed.anchorX() - 1; x <= seed.anchorX() + seed.spanX(); x++) {
                for (int z = seed.anchorZ() - 1; z <= seed.anchorZ() + seed.spanZ(); z++) {
                    if (x >= x0 && x <= x1 && z >= z0 && z <= z1) {
                        int k = (x - x0) * h + (z - z0);
                        if (open[k] && !seen[k]) {
                            seen[k] = true;
                            queue[tail++] = k;
                        }
                    }
                }
            }
            while (head < tail) {
                int k = queue[head++];
                int x = k / h;
                int z = k % h;
                for (int d = 0; d < 4; d++) {
                    int nx = x + (d == 0 ? 1 : d == 1 ? -1 : 0);
                    int nz = z + (d == 2 ? 1 : d == 3 ? -1 : 0);
                    if (nx < 0 || nz < 0 || nx >= w || nz >= h) {
                        continue;
                    }
                    int n = nx * h + nz;
                    if (open[n] && !seen[n] && Math.abs(floor[n] - floor[k]) <= 1.0) {
                        seen[n] = true;
                        queue[tail++] = n;
                    }
                }
            }
            List<NodeView> nodes = view.nodes();
            BitSet reachedNodes = new BitSet(nodes.size());
            for (int i = 0; i < nodes.size(); i++) {
                NodeView n = nodes.get(i);
                boolean reached = n.amount() > 0
                        && touches(seen, x0, z0, w, h, (int) Math.floor(n.anchorX()), (int) Math.floor(n.anchorZ()));
                reachedNodes.set(i, reached);
            }
            List<UnitView> units = view.units();
            BitSet reachedUnits = new BitSet(units.size());
            for (int i = 0; i < units.size(); i++) {
                UnitView u = units.get(i);
                reachedUnits.set(i, touches(seen, x0, z0, w, h, (int) Math.floor(u.x()), (int) Math.floor(u.z())));
            }
            return new Reach(reachedNodes, reachedUnits);
        }

        /** Whether cell (x, z) or one of the eight round it was reached. */
        private static boolean touches(boolean[] seen, int x0, int z0, int w, int h, int x, int z) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int lx = x + dx - x0;
                    int lz = z + dz - z0;
                    if (lx >= 0 && lz >= 0 && lx < w && lz < h && seen[lx * h + lz]) {
                        return true;
                    }
                }
            }
            return false;
        }

        private static boolean inside(@Nullable Footprint f, int x, int z) {
            return f != null
                    && x >= f.anchorX()
                    && x < f.anchorX() + f.spanX()
                    && z >= f.anchorZ()
                    && z < f.anchorZ() + f.spanZ();
        }
    }
}
