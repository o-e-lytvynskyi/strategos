package dev.strategos.core.sim.nav;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.HotPath;
import java.util.Arrays;

/**
 * Builds a {@link SurfacePage} from a {@link VoxelSlice} (04 §9.2, PATH-02): pure, deterministic, and run in jobs, so
 * one builder per thread; it reuses its scratch arrays.
 *
 * <p>Each column is scanned top-down. A voxel with {@link BlockClass#STANDABLE} and a collision top is a floor at {@code
 * y * 16 + top}; its headroom runs up to the next voxel that is not clear (no collision, a door, a climbable or a
 * fluid), or 4 blocks past the band's top. A floor with the mobility class's clearance is a surface, whose kind comes
 * from what stands in it: two blocks of water make it deep, one shallow; a hazard in the floor or the two voxels above
 * makes it a hazard; a door in the clearance a door; a slow block a slow one. At most 4 surfaces a column, topmost
 * first; the rest are counted as overflow. Links join surfaces of neighbouring columns inside the page by {@link
 * #linkable}; diagonals need both orthogonal links. The cost code counts orthogonal neighbours inside the page with no
 * link. Footprint columns of the overlay have no surface.
 */
@CoreApi
@HotPath
public final class PageBuilder {
    private final MobilityClass mobility;
    private final int[] counts = new int[SurfacePage.COLUMNS];
    private final int[] scratch = new int[SurfacePage.COLUMNS * SurfacePage.MAX_LAYERS];

    public PageBuilder(MobilityClass mobility) {
        this.mobility = mobility;
    }

    public SurfacePage build(VoxelSlice slice, NavOverlay overlay, PageCoord coord) {
        return build(slice, overlay, coord, (x, z, floor, clearance) -> floor);
    }

    /** A neighbouring column outside this page, in match-local x/z and slice-relative sixteenths. */
    @CoreApi
    @FunctionalInterface
    public interface Columns {
        int supportingFloor(int x, int z, int floor, int clearance);
    }

    /** Large footprints need the neighbouring pages too; a whole-grid caller supplies their columns. */
    public SurfacePage build(VoxelSlice slice, NavOverlay overlay, PageCoord coord, Columns columns) {
        Arrays.fill(counts, 0);
        int overflow = 0;
        int overflowColumns = 0;
        int lastOverflowColumn = -1;
        int height = slice.height();
        for (int x = 0; x < VoxelSlice.SIDE; x++) {
            for (int z = 0; z < VoxelSlice.SIDE; z++) {
                int column = SurfacePage.column(x, z);
                if (overlay.blocked(coord, column)) {
                    continue;
                }
                for (int y = height - 1; y >= 0; y--) {
                    short c = slice.get(x, y, z);
                    if (!BlockClass.is(c, BlockClass.STANDABLE) || BlockClass.top(c) == 0) {
                        continue;
                    }
                    int floor = y * 16 + BlockClass.top(c);
                    int ceiling = (height + 4) * 16;
                    int water = 0;
                    boolean door = false;
                    boolean hazard = BlockClass.is(c, BlockClass.HAZARD);
                    boolean slow = BlockClass.is(c, BlockClass.SLOW);
                    for (int above = y + 1; above < height; above++) {
                        short a = slice.get(x, above, z);
                        if (!BlockClass.clear(a)) {
                            ceiling = above * 16;
                            break;
                        }
                        if (above - y <= 2 && BlockClass.is(a, BlockClass.HAZARD)) {
                            hazard = true;
                        }
                        if (BlockClass.is(a, BlockClass.WATER) && water == above - y - 1) {
                            water++;
                        }
                        if (BlockClass.is(a, BlockClass.DOOR) && above * 16 < floor + mobility.clearance()) {
                            door = true;
                        }
                        if (above == y + 1 && BlockClass.is(a, BlockClass.SLOW)) {
                            slow = true;
                        }
                    }
                    int headroom = ceiling - floor;
                    if (headroom < mobility.clearance()) {
                        continue;
                    }
                    if (mobility == MobilityClass.LARGE && !fits(slice, overlay, coord, x, z, floor, columns)) {
                        continue;
                    }
                    if (counts[column] == SurfacePage.MAX_LAYERS) {
                        if (lastOverflowColumn != column) {
                            overflowColumns++;
                            lastOverflowColumn = column;
                        }
                        overflow++;
                        continue;
                    }
                    int kind = hazard
                            ? Surface.HAZARD
                            : water >= 2
                                    ? Surface.DEEP_WATER
                                    : water == 1
                                            ? Surface.SHALLOW_WATER
                                            : door ? Surface.DOOR : slow ? Surface.SLOW : Surface.GROUND;
                    scratch[column * SurfacePage.MAX_LAYERS + counts[column]++] =
                            Surface.pack(floor, headroom, kind, 0, 0);
                }
            }
        }
        int[] start = new int[SurfacePage.COLUMNS + 1];
        for (int column = 0; column < SurfacePage.COLUMNS; column++) {
            start[column + 1] = start[column] + counts[column];
        }
        int[] surfaces = new int[start[SurfacePage.COLUMNS]];
        for (int column = 0; column < SurfacePage.COLUMNS; column++) {
            System.arraycopy(scratch, column * SurfacePage.MAX_LAYERS, surfaces, start[column], counts[column]);
        }
        int[] component = new int[surfaces.length];
        short[] layers = new short[surfaces.length];
        long[] drops = link(start, surfaces, component, layers, mobility);
        int components = 0;
        short[] labels = new short[surfaces.length];
        for (int i = 0; i < component.length; i++) {
            components = Math.max(components, component[i] + 1);
            labels[i] = (short) component[i]; // at most 1,024 surfaces a page
        }
        return new SurfacePage(coord, start, surfaces, labels, components, drops, layers, overflow, overflowColumns);
    }

    private boolean fits(
            VoxelSlice slice, NavOverlay overlay, PageCoord coord, int x, int z, int floor, Columns columns) {
        int raised = floor;
        for (int dir = 0; dir < Dir.COUNT; dir++) {
            int nx = x + Dir.dx(dir);
            int nz = z + Dir.dz(dir);
            int support;
            if (nx >= 0 && nz >= 0 && nx < VoxelSlice.SIDE && nz < VoxelSlice.SIDE) {
                if (overlay.blocked(coord, SurfacePage.column(nx, nz))) return false;
                support = supportingFloor(slice, nx, nz, floor, mobility.clearance());
            } else {
                support =
                        columns.supportingFloor(coord.x() * 16 + nx, coord.z() * 16 + nz, floor, mobility.clearance());
            }
            if (support < 0) return false;
            raised = Math.max(raised, support);
        }
        // A step under an edge raises the whole body: its head must still fit in every column.
        if (!clearColumn(slice, x, z, raised, mobility.clearance())) return false;
        for (int dir = 0; dir < Dir.COUNT; dir++) {
            int nx = x + Dir.dx(dir);
            int nz = z + Dir.dz(dir);
            if (nx >= 0 && nz >= 0 && nx < VoxelSlice.SIDE && nz < VoxelSlice.SIDE) {
                if (!clearColumn(slice, nx, nz, raised, mobility.clearance())) return false;
            } else if (columns.supportingFloor(coord.x() * 16 + nx, coord.z() * 16 + nz, raised, mobility.clearance())
                    != raised) {
                return false;
            }
        }
        return true;
    }

    /** A footprint may already rest on a neighbouring step, but never on a wall more than one block up. */
    public static int supportingFloor(VoxelSlice slice, int x, int z, int floor, int clearance) {
        int raised = floor;
        for (int y = floor / 16; y < slice.height() && y * 16 < raised + clearance; y++) {
            short block = slice.get(x, y, z);
            if (BlockClass.is(block, BlockClass.HAZARD)) return -1;
            int top = y * 16 + BlockClass.top(block);
            if (!BlockClass.clear(block) && top > raised) {
                if (!BlockClass.is(block, BlockClass.STANDABLE) || top > floor + MobilityClass.LARGE.step()) return -1;
                raised = top;
            }
        }
        return raised;
    }

    /** Whether a neighbouring column leaves the body's height clear, including partial collision tops. */
    public static boolean clearColumn(VoxelSlice slice, int x, int z, int floor, int clearance) {
        for (int y = floor / 16; y < slice.height() && y * 16 < floor + clearance; y++) {
            short block = slice.get(x, y, z);
            if (!BlockClass.clear(block) && y * 16 + BlockClass.top(block) > floor) return false;
            if (BlockClass.is(block, BlockClass.HAZARD)) return false;
        }
        return true;
    }

    /**
     * Sets the in-page link bits and cost codes, and labels each surface with its local component: surfaces joined by
     * links both ways share one, numbered in order of their first surface. A link the other surface does not return
     * (a drop of more than a step) joins nothing: it is kept as a one-way link between the two components, returned as
     * {@code from << 32 | to}, sorted and distinct.
     */
    private static long[] link(int[] start, int[] surfaces, int[] component, short[] layers, MobilityClass mobility) {
        int[] target = new int[surfaces.length * Dir.COUNT];
        Arrays.fill(target, -1);
        for (int x = 0; x < VoxelSlice.SIDE; x++) {
            for (int z = 0; z < VoxelSlice.SIDE; z++) {
                int column = SurfacePage.column(x, z);
                for (int i = start[column]; i < start[column + 1]; i++) {
                    int s = surfaces[i];
                    if (!Surface.passable(s)) {
                        continue;
                    }
                    int links = 0;
                    int blocked = 0;
                    for (int dir = 0; dir < Dir.COUNT; dir += 2) {
                        int nx = x + Dir.dx(dir);
                        int nz = z + Dir.dz(dir);
                        if (nx < 0 || nz < 0 || nx >= VoxelSlice.SIDE || nz >= VoxelSlice.SIDE) {
                            continue;
                        }
                        int nc = SurfacePage.column(nx, nz);
                        int t = best(start, surfaces, nc, s, mobility);
                        if (t >= 0) {
                            links |= 1 << dir;
                            target[i * Dir.COUNT + dir] = t;
                            layers[i] |= (short) ((t - start[nc]) << (2 * dir));
                        } else {
                            blocked++;
                        }
                    }
                    for (int dir = 1; dir < Dir.COUNT; dir += 2) {
                        if ((links & (1 << Dir.left(dir))) == 0 || (links & (1 << Dir.right(dir))) == 0) {
                            continue;
                        }
                        int nx = x + Dir.dx(dir);
                        int nz = z + Dir.dz(dir);
                        int nc = SurfacePage.column(nx, nz);
                        int t = best(start, surfaces, nc, s, mobility);
                        if (t >= 0) {
                            links |= 1 << dir;
                            target[i * Dir.COUNT + dir] = t;
                            layers[i] |= (short) ((t - start[nc]) << (2 * dir));
                        }
                    }
                    surfaces[i] = Surface.withCost(Surface.withLinks(s, links), blocked);
                }
            }
        }
        int[] parent = new int[surfaces.length];
        for (int i = 0; i < surfaces.length; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < surfaces.length; i++) {
            for (int dir = 0; dir < Dir.COUNT; dir++) {
                int j = target[i * Dir.COUNT + dir];
                if (j >= 0 && target[j * Dir.COUNT + Dir.opposite(dir)] == i) {
                    union(parent, i, j);
                }
            }
        }
        int components = 0;
        for (int i = 0; i < surfaces.length; i++) {
            int root = find(parent, i); // the lowest index of each set is its root, so roots come first
            component[i] = root == i ? components++ : component[root];
        }
        long[] drops = new long[8];
        int n = 0;
        for (int i = 0; i < surfaces.length; i++) {
            for (int dir = 0; dir < Dir.COUNT; dir++) {
                int j = target[i * Dir.COUNT + dir];
                if (j >= 0 && component[i] != component[j]) {
                    if (n == drops.length) {
                        drops = Arrays.copyOf(drops, n * 2);
                    }
                    drops[n++] = (long) component[i] << 32 | component[j];
                }
            }
        }
        return distinct(drops, n);
    }

    /** The first {@code n} values sorted, without repeats. */
    static long[] distinct(long[] values, int n) {
        Arrays.sort(values, 0, n);
        int distinct = 0;
        for (int i = 0; i < n; i++) {
            if (distinct == 0 || values[distinct - 1] != values[i]) {
                values[distinct++] = values[i];
            }
        }
        return Arrays.copyOf(values, distinct);
    }

    /** Joins two union-find sets; the lower index becomes the root, so labels follow surface order. */
    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra < rb) {
            parent[rb] = ra;
        } else if (rb < ra) {
            parent[ra] = rb;
        }
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    /** The index of the surface of {@code column} that {@code from} links to, or -1. */
    private static int best(int[] start, int[] surfaces, int column, int from, MobilityClass mobility) {
        int best = -1;
        int bestRise = Integer.MAX_VALUE;
        for (int i = start[column]; i < start[column + 1]; i++) {
            int to = surfaces[i];
            if (!linkable(from, to, mobility)) {
                continue;
            }
            int rise = Math.abs(Surface.floor(to) - Surface.floor(from));
            if (rise < bestRise) {
                best = i;
                bestRise = rise;
            }
        }
        return best;
    }

    /**
     * Whether a unit can step from {@code from} to {@code to} in a neighbouring column: both passable, the rise within
     * the step, the fall within the drop, and the clearance above the higher floor in both columns. Among several
     * linkable surfaces of one column the one with the smallest rise or fall wins, the upper on a tie (the scan order).
     */
    public static boolean linkable(int from, int to, MobilityClass mobility) {
        if (!Surface.passable(from) || !Surface.passable(to)) {
            return false;
        }
        int rise = Surface.floor(to) - Surface.floor(from);
        if (rise > mobility.step() || rise < -mobility.drop()) {
            return false;
        }
        int high = Math.max(Surface.floor(from), Surface.floor(to));
        return Surface.ceiling(from) >= high + mobility.clearance()
                && Surface.ceiling(to) >= high + mobility.clearance();
    }
}
