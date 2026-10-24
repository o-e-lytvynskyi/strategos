package dev.strategos.core.sim.econ;

import dev.strategos.core.content.Footprint;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.sim.MatchBounds;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.Placing;
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
        long start = s.economyTimes().start();
        int found = search(s, faction, resource, x, z);
        s.economyTimes().dropsite(start);
        return found;
    }

    private static int search(MatchState s, int faction, int resource, double x, double z) {
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

    /** How far from a building's floor the floor of a cell beside it may be for a unit to deliver from it, in blocks. */
    static final double RISE = 2;

    /**
     * As {@link #dropPoint(Footprint, double, double)} for building {@code building}, on ground a unit can stand on:
     * when that cell holds a trunk, a bush, a wall or another footprint, the nearest cell beside the footprint, corners
     * too, with a floor a unit enters within {@value #RISE} blocks of the building's, by distance to (x, z), the lower X
     * and then Z on a tie; the plain point when none has one.
     */
    public static double[] dropPoint(MatchState s, int building, double x, double z) {
        Footprint at = s.footprintOf(building);
        double base = s.buildings().baseY(building) + 1;
        double[] plain = dropPoint(at, x, z);
        if (standable(s, (int) Math.floor(plain[0]), (int) Math.floor(plain[1]), base)) {
            return plain;
        }
        double[] best = plain;
        double bestD = Double.POSITIVE_INFINITY;
        for (int cx = at.anchorX() - 1; cx <= at.anchorX() + at.spanX(); cx++) {
            for (int cz = at.anchorZ() - 1; cz <= at.anchorZ() + at.spanZ(); cz++) {
                boolean ring = cx < at.anchorX()
                        || cz < at.anchorZ()
                        || cx >= at.anchorX() + at.spanX()
                        || cz >= at.anchorZ() + at.spanZ();
                if (!ring || !standable(s, cx, cz, base)) {
                    continue;
                }
                double d = DetMath.length(cx + 0.5 - x, cz + 0.5 - z);
                if (d < bestD) {
                    bestD = d;
                    best = new double[] {cx + 0.5, cz + 0.5};
                }
            }
        }
        return best;
    }

    private static boolean standable(MatchState s, int x, int z, double base) {
        MatchBounds bounds = s.config().bounds();
        if (x < 0 || z < 0 || x >= bounds.sizeX() || z >= bounds.sizeZ() || s.footprintAt(x, z)) {
            return false;
        }
        double floor = Placing.standFloor(s.surfaces(), bounds, x, z);
        return !Double.isNaN(floor) && Math.abs(floor - base) <= RISE;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
