package dev.strategos.core.sim.nav;

import dev.strategos.core.shared.HotPath;

/**
 * Whether a unit can walk straight along a segment (04 §9.3, spec §5.6): a supercover traversal of the columns under
 * it, each step following the grid's link from the surface the walk stands on. Where the segment passes exactly
 * through a corner the step is diagonal, which needs both orthogonal links; elsewhere it crosses one column edge at a
 * time. It fails on a missing link, which also covers impassable surfaces and the grid's edge.
 */
@HotPath
public final class LineOfWalk {
    /** The layer a walk that is inside a gate's columns has (it stands on none). */
    private static final int GATE = -2;

    /** The most a walk out of a gate may rise or fall from the floor it left, in sixteenths: a block. */
    private static final int STEP = 16;

    private LineOfWalk() {}

    /** The layer of column ({@code x}, {@code z}) whose passable surface is nearest {@code floor} within a block, or -1. */
    static int landing(SurfaceGrid grid, int x, int z, int floor) {
        if (x < 0 || z < 0 || x >= grid.sizeX() || z >= grid.sizeZ()) {
            return -1;
        }
        int best = -1;
        int distance = STEP + 1;
        for (int layer = 0; layer < grid.count(x, z); layer++) {
            int surface = grid.surface(x, z, layer);
            int d = Math.abs(Surface.floor(surface) - floor);
            if (Surface.passable(surface) && d < distance) {
                best = layer;
                distance = d;
            }
        }
        return best;
    }

    /**
     * The node the walk ends on, in the column of ({@code x1}, {@code z1}), starting on node {@code from}, whose
     * column holds ({@code x0}, {@code z0}); -1 if a step has no link. Match-local blocks.
     */
    public static int walk(SurfaceGrid grid, int from, double x0, double z0, double x1, double z1) {
        return walk(grid, from, x0, z0, x1, z1, null);
    }

    /**
     * As {@link #walk(SurfaceGrid, int, double, double, double, double)}, a faction's {@code gates} walkable too
     * (PATH-12): an orthogonal step into a column of an allied gate, which has no surface, goes on through the gate's
     * columns and ends on a surface within a block of the floor it left; null for no gates.
     */
    public static int walk(
            SurfaceGrid grid,
            int from,
            double x0,
            double z0,
            double x1,
            double z1,
            GateLinks.@org.jspecify.annotations.Nullable Pass gates) {
        int cx = grid.nodeX(from);
        int cz = grid.nodeZ(from);
        int layer = SurfaceGrid.nodeLayer(from);
        int floor = 0; // the floor a walk left for a gate, in sixteenths
        int ex = (int) Math.floor(x1);
        int ez = (int) Math.floor(z1);
        double dx = x1 - x0;
        double dz = z1 - z0;
        int sx = dx > 0 ? 1 : dx < 0 ? -1 : 0;
        int sz = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        double deltaX = sx != 0 ? 1.0 / Math.abs(dx) : Double.POSITIVE_INFINITY;
        double deltaZ = sz != 0 ? 1.0 / Math.abs(dz) : Double.POSITIVE_INFINITY;
        double nextX = sx > 0 ? (cx + 1 - x0) * deltaX : sx < 0 ? (x0 - cx) * deltaX : Double.POSITIVE_INFINITY;
        double nextZ = sz > 0 ? (cz + 1 - z0) * deltaZ : sz < 0 ? (z0 - cz) * deltaZ : Double.POSITIVE_INFINITY;
        int steps = Math.abs(ex - cx) + Math.abs(ez - cz);
        for (int taken = 0; cx != ex || cz != ez; taken++) {
            if (taken >= steps) {
                return -1; // rounding carried the walk past the end column
            }
            int dir;
            if (nextX < nextZ) {
                dir = sx > 0 ? Dir.E : Dir.W;
            } else if (nextZ < nextX) {
                dir = sz > 0 ? Dir.S : Dir.N;
            } else {
                dir = diagonal(sx, sz);
            }
            int next = layer < 0 ? -1 : grid.neighbour(cx, cz, layer, dir);
            if (next < 0 && gates != null && !Dir.diagonal(dir)) {
                int tx = cx + Dir.dx(dir);
                int tz = cz + Dir.dz(dir);
                if (gates.gate(tx, tz)) {
                    if (layer >= 0) {
                        floor = Surface.floor(grid.surface(cx, cz, layer)); // into a gate: no surface, the floor kept
                    }
                    next = GATE;
                } else if (layer < 0) {
                    next = landing(grid, tx, tz, floor); // out of the gate
                }
            }
            if (next < 0 && next != GATE) {
                return -1;
            }
            layer = next == GATE ? -1 : next;
            if (dir != Dir.N && dir != Dir.S) {
                cx += sx;
                nextX += deltaX;
            }
            if (dir != Dir.E && dir != Dir.W) {
                cz += sz;
                nextZ += deltaZ;
            }
        }
        return layer < 0 ? -1 : grid.node(cx, cz, layer);
    }

    /** Whether the walk from {@code from} at its column's centre to node {@code to}'s centre ends on {@code to}. */
    public static boolean between(SurfaceGrid grid, int from, int to) {
        return walk(
                        grid,
                        from,
                        grid.nodeX(from) + 0.5,
                        grid.nodeZ(from) + 0.5,
                        grid.nodeX(to) + 0.5,
                        grid.nodeZ(to) + 0.5)
                == to;
    }

    private static int diagonal(int sx, int sz) {
        if (sx > 0) {
            return sz > 0 ? Dir.SE : Dir.NE;
        }
        return sz > 0 ? Dir.SW : Dir.NW;
    }
}
