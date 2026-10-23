package dev.strategos.core.sim.econ;

import dev.strategos.core.content.Footprint;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.build.BuildingStore;
import java.util.List;

/**
 * Where a gatherer takes its load (ECO-05, 04 §11.4): its faction's nearest finished building whose
 * {@code Dropsite/resources} lists the resource, read through the faction's modifiers, measured to the edge of its
 * footprint; the lower handle on a tie. A load is delivered from the cell beside that edge nearest the gatherer.
 */
@CoreApi
public final class Dropsites {
    /** The token path of the resources a building takes in. */
    public static final String RESOURCES = "Dropsite/resources";

    /** How far from a footprint's edge a gatherer may stand and still deliver, in blocks. */
    public static final double REACH = 1.5;

    private Dropsites() {}

    /** Faction {@code faction}'s dropsite nearest (x, z) that takes resource {@code resource}, or 0. */
    public static int nearest(MatchState s, int faction, int resource, double x, double z) {
        BuildingStore b = s.buildings();
        if (b.empty()) {
            return 0;
        }
        int path = s.stats().tokenPath(RESOURCES);
        if (path < 0) {
            return 0;
        }
        String id = s.config().rules().economy().resources().get(resource).id();
        int best = 0;
        double bestD = Double.POSITIVE_INFINITY;
        for (int handle : b.handles()) {
            if (b.faction(handle) != faction || b.state(handle) != BuildingStore.COMPLETE) {
                continue;
            }
            List<String> takes = s.stats().blockTokens(b.template(handle), faction, path);
            if (takes == null || !takes.contains(id)) {
                continue;
            }
            double d = distance(s.footprintOf(handle), x, z);
            if (d < bestD) { // handles ascend, so the lower wins a tie
                best = handle;
                bestD = d;
            }
        }
        return best;
    }

    /** Whether building {@code handle} still takes in {@code resource} from {@code faction}'s gatherers. */
    public static boolean takes(MatchState s, int handle, int faction, int resource) {
        BuildingStore b = s.buildings();
        if (!b.alive(handle) || b.faction(handle) != faction || b.state(handle) != BuildingStore.COMPLETE) {
            return false;
        }
        int path = s.stats().tokenPath(RESOURCES);
        List<String> takes = path < 0 ? null : s.stats().blockTokens(b.template(handle), faction, path);
        return takes != null
                && takes.contains(
                        s.config().rules().economy().resources().get(resource).id());
    }

    /** The distance from (x, z) to footprint {@code at}'s edge, 0 inside it. */
    public static double distance(Footprint at, double x, double z) {
        double dx = Math.max(0, Math.max(at.anchorX() - x, x - (at.anchorX() + at.spanX())));
        double dz = Math.max(0, Math.max(at.anchorZ() - z, z - (at.anchorZ() + at.spanZ())));
        return DetMath.length(dx, dz);
    }

    /**
     * The centre of the cell beside footprint {@code at}'s edge nearest (x, z), as {@code {x, z}}: on the side the point
     * lies farthest out from, at the cell along that side nearest the point.
     */
    public static double[] dropPoint(Footprint at, double x, double z) {
        int x0 = at.anchorX();
        int z0 = at.anchorZ();
        int x1 = x0 + at.spanX() - 1;
        int z1 = z0 + at.spanZ() - 1;
        int cx = clamp((int) Math.floor(x), x0, x1);
        int cz = clamp((int) Math.floor(z), z0, z1);
        double west = x0 - x;
        double east = x - (x1 + 1);
        double north = z0 - z;
        double south = z - (z1 + 1);
        double most = Math.max(Math.max(west, east), Math.max(north, south));
        if (most == west) {
            cx = x0 - 1;
        } else if (most == east) {
            cx = x1 + 1;
        } else if (most == north) {
            cz = z0 - 1;
        } else {
            cz = z1 + 1;
        }
        return new double[] {cx + 0.5, cz + 0.5};
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
