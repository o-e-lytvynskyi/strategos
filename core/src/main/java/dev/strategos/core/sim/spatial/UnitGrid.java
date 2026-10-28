package dev.strategos.core.sim.spatial;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.shared.HotPath;
import dev.strategos.core.sim.EntityStore;
import dev.strategos.core.sim.IdentityStore;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.garrison.GarrisonStore;
import java.util.Arrays;
import java.util.function.IntConsumer;
import org.jspecify.annotations.Nullable;

/**
 * Live units in a uniform grid of {@value #CELL}-block cells over the match space (04 §10.1), rebuilt whole at the end
 * of each tick's movement by a counting sort of (cell, unit index): no incremental bookkeeping, index order inside each
 * cell, and per cell the bitmask of the factions in it, so a query skips cells without a faction it wants. A unit
 * outside the space counts in the border cell nearest it. Queries report unit indices in ascending order (the handle's
 * slot, the order systems iterate in), through a bitmask of the units found rather than a sort, and allocate nothing;
 * one must not start inside another's consumer.
 */
@HotPath
public final class UnitGrid {
    public static final int CELL = 8;

    private final int cellsX;
    private final int cellsZ;
    private long[] templateClasses = new long[0];
    private final int[] start; // per cell its first entry; the last holds the count
    private final int[] next;
    private final int[] factions;
    private int[] cellOf = new int[0]; // per unit index during a rebuild; -1 for a free slot
    private int[] units = new int[0]; // per entry: the unit index, by cell and then index
    private double[] xs = new double[0];
    private double[] zs = new double[0];
    private int[] unitFaction = new int[0];
    private long[] unitClasses = new long[0];
    private int[] found = new int[0];
    private long[] marks = new long[0]; // per 64 unit indices, those the running query found
    private int count;
    private long builtAt = -1; // the motion store's writes when the last rebuild read it
    private boolean reporting;

    public UnitGrid(int sizeX, int sizeZ, TemplateSet templates) {
        this.cellsX = Math.max(1, (sizeX + CELL - 1) / CELL);
        this.cellsZ = Math.max(1, (sizeZ + CELL - 1) / CELL);
        this.start = new int[cellsX * cellsZ + 1];
        this.next = new int[cellsX * cellsZ];
        this.factions = new int[cellsX * cellsZ];
        swap(templates);
    }

    /** Each template's class bits from {@code templates}, a new set's after a swap (DATA-03). */
    public void swap(TemplateSet templates) {
        templateClasses = new long[templates.indexCount()];
        for (UnitTemplate t : templates.all()) {
            templateClasses[t.netIndex()] = templates.tokens().bits(t.classes());
        }
    }

    /** Every live unit, by cell; allocates only when the match has more unit slots than ever before. */
    public void rebuild(EntityStore entities, MotionStore motion, IdentityStore identity) {
        rebuild(entities, motion, identity, null);
    }

    /** As {@link #rebuild(EntityStore, MotionStore, IdentityStore)}, but the units inside a building (BLD-16) are left out. */
    public void rebuild(
            EntityStore entities, MotionStore motion, IdentityStore identity, @Nullable GarrisonStore garrison) {
        fill(entities, motion, identity, garrison);
        builtAt = motion.writes();
    }

    private void fill(
            EntityStore entities, MotionStore motion, IdentityStore identity, @Nullable GarrisonStore garrison) {
        int highWater = entities.highWater();
        if (highWater > cellOf.length) {
            grow(highWater);
        }
        Arrays.fill(start, 0);
        Arrays.fill(factions, 0);
        int n = 0;
        for (int i = 0; i < highWater; i++) {
            if (entities.aliveAt(i)) {
                int c = cell(motion.x(i), motion.z(i));
                cellOf[i] = c;
                start[c + 1]++;
                n++;
            } else {
                cellOf[i] = -1;
            }
        }
        if (garrison != null && !garrison.empty()) { // units inside a building are in no cell (BLD-16)
            for (int i = 0; i < highWater; i++) {
                if (cellOf[i] >= 0 && garrison.inside(i)) {
                    start[cellOf[i] + 1]--;
                    cellOf[i] = -1;
                    n--;
                }
            }
        }
        int cells = cellsX * cellsZ;
        for (int c = 0; c < cells; c++) {
            start[c + 1] += start[c];
        }
        System.arraycopy(start, 0, next, 0, cells);
        for (int i = 0; i < highWater; i++) {
            int c = cellOf[i];
            if (c >= 0) {
                int at = next[c]++;
                int faction = identity.faction(i);
                units[at] = i;
                xs[at] = motion.x(i);
                zs[at] = motion.z(i);
                unitFaction[at] = faction;
                unitClasses[at] = templateClasses[identity.templateIndex(i)];
                factions[c] |= 1 << faction;
            }
        }
        count = n;
    }

    /**
     * Whether no pose was set since the last rebuild, so every position the grid holds is the unit's own: a query's
     * cell and distance tests then agree with a read of the motion store.
     */
    public boolean matches(MotionStore motion) {
        return builtAt == motion.writes();
    }

    /** The units the last rebuild placed. */
    public int count() {
        return count;
    }

    /** Calls {@code consumer} with every unit within {@code r} of (x, z) that passes {@code filter}, by index. */
    public void forEachInRadius(double x, double z, double r, UnitFilter filter, IntConsumer consumer) {
        if (reporting) {
            throw new IllegalStateException("a spatial query inside another's consumer");
        }
        int n = collect(x, z, r, filter);
        if (n == 0) {
            return;
        }
        int first = Integer.MAX_VALUE;
        int last = -1;
        for (int k = 0; k < n; k++) {
            int unit = found[k];
            int word = unit >>> 6;
            marks[word] |= 1L << unit;
            first = Math.min(first, word);
            last = Math.max(last, word);
        }
        reporting = true;
        try {
            for (int word = first; word <= last; word++) {
                long bits = marks[word];
                marks[word] = 0;
                while (bits != 0) {
                    int bit = Long.numberOfTrailingZeros(bits);
                    bits &= bits - 1;
                    consumer.accept((word << 6) | bit);
                }
            }
        } finally {
            reporting = false;
            Arrays.fill(marks, first, last + 1, 0); // what a consumer that threw left unreported
        }
    }

    /**
     * As {@link #forEachInRadius}, but in the grid's own order (by cell, then index) and straight from the scan: for a
     * consumer whose result does not depend on the order it is called in. It skips the ascending-order pass, which a
     * query run per unit per tick pays for in full.
     */
    public void forEachInRadiusAnyOrder(double x, double z, double r, UnitFilter filter, IntConsumer consumer) {
        if (reporting) {
            throw new IllegalStateException("a spatial query inside another's consumer");
        }
        int n = collect(x, z, r, filter);
        reporting = true;
        try {
            for (int k = 0; k < n; k++) {
                consumer.accept(found[k]);
            }
        } finally {
            reporting = false;
        }
    }

    /** How many units within {@code r} of (x, z) pass {@code filter}. */
    public int countWithin(double x, double z, double r, UnitFilter filter) {
        return collect(x, z, r, filter);
    }

    /** The unit nearest (x, z) that passes {@code filter}, the lower index on a tie; -1 when none does. */
    public int nearest(double x, double z, UnitFilter filter) {
        return nearest(x, z, Double.POSITIVE_INFINITY, filter);
    }

    /**
     * The unit nearest (x, z) within {@code r} that passes {@code filter}, the lower index on a tie; -1 when none does.
     * Only the cells that reach within {@code r} are looked at.
     */
    public int nearest(double x, double z, double r, UnitFilter filter) {
        int qx = clamp((int) Math.floor(x / CELL), cellsX);
        int qz = clamp((int) Math.floor(z / CELL), cellsZ);
        int best = -1;
        double bestD2 = r * r;
        int rings = Math.max(cellsX, cellsZ);
        if (r < Double.POSITIVE_INFINITY) {
            rings = Math.min(rings, (int) Math.ceil(r / CELL) + 1);
        } else {
            bestD2 = Double.POSITIVE_INFINITY;
        }
        for (int ring = 0; ring <= rings; ring++) {
            if (best >= 0 && ring > 0) {
                double bound = outside(x, z, qx, qz, ring - 1);
                if (bound * bound > bestD2) {
                    break; // every cell from this ring on lies farther than the best
                }
            }
            for (int cz = qz - ring; cz <= qz + ring; cz++) {
                if (cz < 0 || cz >= cellsZ) {
                    continue;
                }
                boolean edgeRow = cz == qz - ring || cz == qz + ring;
                int step = edgeRow || ring == 0 ? 1 : 2 * ring;
                for (int cx = qx - ring; cx <= qx + ring; cx += step) {
                    if (cx < 0 || cx >= cellsX) {
                        continue;
                    }
                    int c = cz * cellsX + cx;
                    if ((factions[c] & filter.factions()) == 0) {
                        continue;
                    }
                    for (int e = start[c]; e < start[c + 1]; e++) {
                        double dx = xs[e] - x;
                        double dz = zs[e] - z;
                        double d2 = dx * dx + dz * dz;
                        if ((d2 < bestD2 || (d2 == bestD2 && (best < 0 || units[e] < best)))
                                && filter.passes(unitFaction[e], unitClasses[e])) {
                            best = units[e];
                            bestD2 = d2;
                        }
                    }
                }
            }
        }
        return best;
    }

    /**
     * How far (x, z) is from leaving the square of cells within {@code within} rings of (qx, qz): no unit of a cell
     * outside it is nearer. 0 when the point is not inside that square.
     */
    private static double outside(double x, double z, int qx, int qz, int within) {
        double x0 = (double) (qx - within) * CELL;
        double x1 = (double) (qx + within + 1) * CELL;
        double z0 = (double) (qz - within) * CELL;
        double z1 = (double) (qz + within + 1) * CELL;
        double d = Math.min(Math.min(x - x0, x1 - x), Math.min(z - z0, z1 - z));
        return Math.max(0, d);
    }

    private int collect(double x, double z, double r, UnitFilter filter) {
        double r2 = r * r;
        int cx0 = clamp((int) Math.floor((x - r) / CELL), cellsX);
        int cx1 = clamp((int) Math.floor((x + r) / CELL), cellsX);
        int cz0 = clamp((int) Math.floor((z - r) / CELL), cellsZ);
        int cz1 = clamp((int) Math.floor((z + r) / CELL), cellsZ);
        int n = 0;
        for (int cz = cz0; cz <= cz1; cz++) {
            for (int cx = cx0; cx <= cx1; cx++) {
                int c = cz * cellsX + cx;
                if ((factions[c] & filter.factions()) == 0) {
                    continue;
                }
                for (int e = start[c]; e < start[c + 1]; e++) {
                    double dx = xs[e] - x;
                    double dz = zs[e] - z;
                    if (dx * dx + dz * dz <= r2 && filter.passes(unitFaction[e], unitClasses[e])) {
                        found[n++] = units[e];
                    }
                }
            }
        }
        return n;
    }

    private int cell(double x, double z) {
        return clamp((int) Math.floor(z / CELL), cellsZ) * cellsX + clamp((int) Math.floor(x / CELL), cellsX);
    }

    private static int clamp(int v, int limit) {
        return Math.max(0, Math.min(limit - 1, v));
    }

    private void grow(int highWater) {
        int capacity = Math.max(highWater, cellOf.length * 2);
        cellOf = new int[capacity];
        units = new int[capacity];
        xs = new double[capacity];
        zs = new double[capacity];
        unitFaction = new int[capacity];
        unitClasses = new long[capacity];
        found = new int[capacity];
        marks = new long[(capacity + 63) >>> 6];
    }
}
