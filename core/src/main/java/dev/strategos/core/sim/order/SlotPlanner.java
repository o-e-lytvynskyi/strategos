package dev.strategos.core.sim.order;

import dev.strategos.core.shared.SlotLayout;
import dev.strategos.core.sim.MatchBounds;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.nav.PathService;
import dev.strategos.core.sim.nav.Spiral;
import dev.strategos.core.sim.nav.Surface;
import dev.strategos.core.sim.nav.SurfaceGrid;
import java.util.Arrays;
import java.util.Comparator;
import org.jspecify.annotations.Nullable;

/**
 * Each member's place in a group order (ORD-03, spec §5.9). The layout ({@link SlotLayout}) is centred on the target
 * and faces from the group's centroid to it; a place beyond the match's edge is drawn back inside. On terrain each
 * place is snapped to the nearest surface within
 * {@value #SNAP} blocks, in a spiral of fixed order, whose component is the target's, skipping candidates nearer than
 * {@link SlotLayout#MIN_SPACING} to an earlier place; a place with none keeps its point, which the path service makes
 * reachable. Members are paired with places column by column, the front places first, so no member has to pass
 * members already standing ({@code assign}). For a block or a line heading toward the target no two straight ways
 * cross; a single file or a slant may cross some, and avoidance takes those round each other.
 */
public final class SlotPlanner {
    static final int SNAP = 3;

    /** The spiral: (dx, dz) within {@value #SNAP} columns. */
    private final int[] spiral;

    private double[] xs = new double[0];
    private double[] zs = new double[0];
    private int[] slots = new int[0];

    public SlotPlanner() {
        spiral = Spiral.of(SNAP);
    }

    /** Plans the places of the units at {@code indices} for a group order to (tx, tz); read them by position. */
    public void plan(MatchState s, int[] indices, double tx, double tz) {
        int n = indices.length;
        xs = new double[n];
        zs = new double[n];
        slots = new int[n];
        MotionStore motion = s.motion();
        double cx = 0;
        double cy = 0;
        double cz = 0;
        double radius = 0;
        for (int i : indices) {
            cx += motion.x(i);
            cy += motion.y(i);
            cz += motion.z(i);
            radius = Math.max(
                    radius, s.templates().byIndex(s.identity().templateIndex(i)).radius());
        }
        cx /= n;
        cy /= n;
        cz /= n;
        double dx = tx - cx;
        double dz = tz - cz;
        double length = Math.sqrt(dx * dx + dz * dz);
        double fx = length > 1e-9 ? dx / length : 1;
        double fz = length > 1e-9 ? dz / length : 0;
        double spacing = SlotLayout.spacing(radius);
        int width = SlotLayout.width(n);
        double[] px = new double[n];
        double[] pz = new double[n];
        MatchBounds bounds = s.config().bounds();
        for (int k = 0; k < n; k++) {
            // a place beyond the match's edge is drawn back inside, half a block from it
            px[k] = Math.max(0.5, Math.min(bounds.sizeX() - 0.5, SlotLayout.x(k, n, width, spacing, tx, fx, fz)));
            pz[k] = Math.max(0.5, Math.min(bounds.sizeZ() - 0.5, SlotLayout.z(k, n, width, spacing, tz, fx, fz)));
        }
        snap(s, px, pz, tx, cy, tz);
        assign(s, indices, cx, cz, fx, fz, n, width, spacing, px, pz);
        snapLarge(s, indices, tx, cy, tz);
    }

    /** After matching units to places, each large member's final place must fit its own grid. */
    private void snapLarge(MatchState s, int[] indices, double tx, double y, double tz) {
        for (int k = 0; k < indices.length; k++) {
            if (s.mobility(indices[k]) != dev.strategos.core.sim.nav.MobilityClass.LARGE) continue;
            SurfaceGrid grid = s.surfacesFor(indices[k]);
            if (grid.version() == 0) continue;
            int home =
                    PathService.startNode(grid, tx, y, tz, s.config().bounds().minY());
            int component = home < 0 ? -1 : grid.component(home);
            int ox = (int) Math.floor(xs[k]);
            int oz = (int) Math.floor(zs[k]);
            for (int c = 0; c < spiral.length; c += 2) {
                int x = ox + spiral[c];
                int z = oz + spiral[c + 1];
                if (!grid.inside(x, z) || !holds(grid, x, z, component)) continue;
                double candidateX = c == 0 ? xs[k] : x + 0.5;
                double candidateZ = c == 0 ? zs[k] : z + 0.5;
                if (crowdsAssigned(s, indices, k, candidateX, candidateZ)) continue;
                xs[k] = candidateX;
                zs[k] = candidateZ;
                break;
            }
        }
    }

    private boolean crowdsAssigned(MatchState s, int[] indices, int k, double x, double z) {
        double radius =
                s.templates().byIndex(s.identity().templateIndex(indices[k])).radius();
        for (int j = 0; j < indices.length; j++) {
            if (j == k) continue;
            double other = s.templates()
                    .byIndex(s.identity().templateIndex(indices[j]))
                    .radius();
            double dx = xs[j] - x;
            double dz = zs[j] - z;
            if (dx * dx + dz * dz < (radius + other) * (radius + other)) return true;
        }
        return false;
    }

    /** The planned X of the unit at position {@code k} of the indices given. */
    public double x(int k) {
        return xs[k];
    }

    public double z(int k) {
        return zs[k];
    }

    /** Its place's number, from 1: 0 is an order without a place. */
    public int slot(int k) {
        return slots[k];
    }

    private void snap(MatchState s, double[] px, double[] pz, double tx, double y, double tz) {
        SurfaceGrid grid = s.surfaces();
        if (grid.version() == 0) {
            return; // no terrain: the places stand as laid out
        }
        int minY = s.config().bounds().minY();
        int home = PathService.startNode(grid, tx, y, tz, minY);
        int component = home < 0 ? -1 : grid.component(home);
        for (int k = 0; k < px.length; k++) {
            int ox = (int) Math.floor(px[k]);
            int oz = (int) Math.floor(pz[k]);
            for (int c = 0; c < spiral.length; c += 2) {
                int x = ox + spiral[c];
                int z = oz + spiral[c + 1];
                if (!grid.inside(x, z) || !holds(grid, x, z, component)) {
                    continue;
                }
                double cxk = c == 0 ? px[k] : x + 0.5;
                double czk = c == 0 ? pz[k] : z + 0.5;
                if (crowds(px, pz, k, cxk, czk)) {
                    continue;
                }
                px[k] = cxk;
                pz[k] = czk;
                break;
            }
        }
    }

    /** Whether the column has a passable surface of {@code component} (any, for -1). */
    private static boolean holds(SurfaceGrid grid, int x, int z, int component) {
        int count = grid.count(x, z);
        for (int layer = 0; layer < count; layer++) {
            if (Surface.passable(grid.surface(x, z, layer))
                    && (component < 0 || grid.component(grid.node(x, z, layer)) == component)) {
                return true;
            }
        }
        return false;
    }

    /** Whether (x, z) lies nearer than the least spacing to one of the places before {@code k}. */
    private static boolean crowds(double[] px, double[] pz, int k, double x, double z) {
        for (int j = 0; j < k; j++) {
            double dx = px[j] - x;
            double dz = pz[j] - z;
            if (Math.sqrt(dx * dx + dz * dz) < SlotLayout.MIN_SPACING - 1e-9) {
                return true;
            }
        }
        return false;
    }

    /**
     * Pairs units with places column by column: units sorted across the facing (then along it, then by index) fill the
     * places' columns from the left, and in each column both are sorted along the facing, the farthest first, and
     * paired, so the front places fill first and no member has to pass members already standing; members level with
     * each other go the farthest from the column to the farthest place. Filling by rows for a group strung out along
     * the facing, and swapping crossing ways, were tried and each walled a member out of its place in a crowd.
     */
    private void assign(
            MatchState s,
            int[] indices,
            double cx,
            double cz,
            double fx,
            double fz,
            int n,
            int width,
            double spacing,
            double[] px,
            double[] pz) {
        MotionStore motion = s.motion();
        double[] uLat = new double[n];
        double[] uDepth = new double[n];
        double[] pLat = new double[n];
        double[] pDepth = new double[n];
        for (int k = 0; k < n; k++) {
            double rx = motion.x(indices[k]) - cx;
            double rz = motion.z(indices[k]) - cz;
            uLat[k] = -rx * fz + rz * fx;
            uDepth[k] = rx * fx + rz * fz;
            pLat[k] = SlotLayout.lateral(k, n, width, spacing);
            pDepth[k] = SlotLayout.depth(k, n, width, spacing);
        }
        double[] uMajor = uLat;
        double[] uMinor = uDepth;
        Integer[] units = order(n, uMajor, uMinor, indices);
        Integer[] places = order(n, pLat, pDepth, null);
        int from = 0;
        while (from < n) {
            double key = pLat[places[from]];
            int to = from;
            while (to < n && pLat[places[to]] == key) {
                to++;
            }
            Integer[] line = Arrays.copyOfRange(units, from, to);
            // along the facing, the farthest first; level members, the farthest from the column first, so their ways
            // do not cross
            Arrays.sort(
                    line,
                    Comparator.<Integer>comparingDouble(k -> -uMinor[k])
                            .thenComparingDouble(k -> -Math.abs(uMajor[k] - key))
                            .thenComparingInt(k -> indices[k]));
            for (int j = 0; j < line.length; j++) {
                int unit = line[j];
                int place = places[from + j];
                xs[unit] = px[place];
                zs[unit] = pz[place];
                slots[unit] = place + 1;
            }
            from = to;
        }
    }

    /** Positions 0..n-1 sorted by {@code major}, then {@code minor}, both largest first, then by index or position. */
    private static Integer[] order(int n, double[] major, double[] minor, int @Nullable [] indices) {
        Integer[] out = new Integer[n];
        for (int k = 0; k < n; k++) {
            out[k] = k;
        }
        Arrays.sort(
                out,
                Comparator.<Integer>comparingDouble(k -> -major[k])
                        .thenComparingDouble(k -> -minor[k])
                        .thenComparingInt(k -> indices == null ? k : indices[k]));
        return out;
    }
}
