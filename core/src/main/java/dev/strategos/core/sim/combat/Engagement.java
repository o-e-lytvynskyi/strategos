package dev.strategos.core.sim.combat;

import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.OrderQueues;
import java.util.Arrays;

/**
 * Where melee attackers stand round their target (CMB-02.2): rings of places about the target, the first
 * {@value #GAP} blocks out from the two bodies' edges and each next {@value #RING_STEP} farther, with places
 * {@value #SPACING} blocks apart, centre to centre, so neighbours do not overlap. Places are numbered ring by ring
 * from angle 0, each ring turned half a place from the last. An attacker takes the free place of the innermost ring
 * that has one, the nearest to it there; the places taken are those of every unit attacking the same target, near or
 * still on its way, read from their orders, so nothing else needs keeping.
 */
public final class Engagement {
    public static final double GAP = 0.15;
    public static final double RING_STEP = 0.75;
    public static final double SPACING = 0.8;
    public static final int RINGS = 4;
    /** The most places round one target: the four rings' worth for the largest bodies is far below this. */
    public static final int MAX_PLACES = 128;

    private final boolean[] taken = new boolean[MAX_PLACES];

    /** The ring place {@code place} lies on, for attacker and target radii summing to {@code radii}. */
    public static int ring(int place, double radii) {
        int first = 0;
        for (int k = 0; k < RINGS; k++) {
            int n = places(k, radii);
            if (place < first + n) {
                return k;
            }
            first += n;
        }
        return RINGS - 1;
    }

    /** How many places ring {@code k} holds. */
    public static int places(int k, double radii) {
        double r = radii + GAP + k * RING_STEP;
        return Math.max(4, (int) Math.floor(2 * Math.PI * r / SPACING));
    }

    /** The places of all rings. */
    public static int total(double radii) {
        int n = 0;
        for (int k = 0; k < RINGS; k++) {
            n += places(k, radii);
        }
        return Math.min(n, MAX_PLACES);
    }

    /** The X offset of place {@code place} from its target's centre. */
    public static double offsetX(int place, double radii) {
        return radius(place, radii) * DetMath.cos(angle(place, radii));
    }

    /** The Z offset of place {@code place} from its target's centre. */
    public static double offsetZ(int place, double radii) {
        return radius(place, radii) * DetMath.sin(angle(place, radii));
    }

    private static double radius(int place, double radii) {
        return radii + GAP + ring(place, radii) * RING_STEP;
    }

    private static double angle(int place, double radii) {
        int k = ring(place, radii);
        int first = 0;
        for (int j = 0; j < k; j++) {
            first += places(j, radii);
        }
        int n = places(k, radii);
        return 2 * Math.PI * ((place - first) + (k % 2) * 0.5) / n;
    }

    /**
     * The place unit {@code i} takes round {@code target}: the innermost ring's free place nearest to it, among those
     * the other attackers of {@code target} have not taken.
     */
    public int choose(MatchState s, int i, int target) {
        int t = Handle.index(target);
        double radii = radius(s, i) + radius(s, t);
        int total = total(radii);
        Arrays.fill(taken, 0, total, false);
        double tx = s.motion().x(t);
        double tz = s.motion().z(t);
        OrderQueues orders = s.orders();
        for (int j = 0; j < s.entities().highWater(); j++) { // attackers still on their way count too
            if (j != i && s.entities().aliveAt(j) && OrderKind.attacks(orders.kind(j)) && orders.target(j) == target) {
                int place = orders.slot(j);
                if (place >= 0 && place < total) {
                    taken[place] = true;
                }
            }
        }
        double x = s.motion().x(i);
        double z = s.motion().z(i);
        int first = 0;
        for (int k = 0; k < RINGS; k++) {
            int n = places(k, radii);
            int best = -1;
            double bestD = Double.POSITIVE_INFINITY;
            for (int p = first; p < Math.min(first + n, total); p++) {
                if (taken[p]) {
                    continue;
                }
                double d = DetMath.length(tx + offsetX(p, radii) - x, tz + offsetZ(p, radii) - z);
                if (d < bestD) {
                    best = p;
                    bestD = d;
                }
            }
            if (best >= 0) {
                return best;
            }
            first += n;
        }
        return 0; // every place is taken: the first ring's first, behind the others
    }

    /** The radius of unit {@code i}'s body. */
    public static double radius(MatchState s, int i) {
        return s.templates().byIndex(s.identity().templateIndex(i)).radius();
    }

    /** How far apart the edges of units {@code i} and {@code j} are. */
    public static double gap(MatchState s, int i, int j) {
        double d = DetMath.length(
                s.motion().x(j) - s.motion().x(i), s.motion().z(j) - s.motion().z(i));
        return d - radius(s, i) - radius(s, j);
    }
}
