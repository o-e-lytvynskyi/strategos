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
        Arrays.fill(counts, 0);
        int overflow = 0;
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
                    if (counts[column] == SurfacePage.MAX_LAYERS) {
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
        link(start, surfaces);
        return new SurfacePage(coord, start, surfaces, overflow);
    }

    /** Sets the in-page link bits and cost codes. */
    private void link(int[] start, int[] surfaces) {
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
                        if (best(start, surfaces, SurfacePage.column(nx, nz), s) >= 0) {
                            links |= 1 << dir;
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
                        if (best(start, surfaces, SurfacePage.column(nx, nz), s) >= 0) {
                            links |= 1 << dir;
                        }
                    }
                    surfaces[i] = Surface.withCost(Surface.withLinks(s, links), blocked);
                }
            }
        }
    }

    /** The index of the surface of {@code column} that {@code from} links to, or -1. */
    private int best(int[] start, int[] surfaces, int column, int from) {
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
