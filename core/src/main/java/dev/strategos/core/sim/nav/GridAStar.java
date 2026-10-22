package dev.strategos.core.sim.nav;

import dev.strategos.core.shared.HotPath;
import java.util.Arrays;

/**
 * A* over the surfaces of a {@link SurfaceGrid} (04 §9.3, spec §5.6). Eight neighbours along the grid's links; a step
 * costs 1 or √2 times the destination's factor (shallow water 4, a door 1.5, slow ground 2), plus 0.5 for a rise of 4
 * sixteenths or more and 0.1 per point of the destination's cost code. The heuristic is the octile distance to the goal
 * column with weight 1: no step costs less than its distance, so the first goal-column node expanded ends a cheapest
 * path. The open set is a binary heap ordered by f, then h, then node id.
 *
 * <p>Scratch state is kept per discovered node in arrays that grow with the search, and found through a primitive
 * open-addressing map whose entries are stamped with the search they belong to: memory follows the cap, never the grid,
 * nothing is cleared between searches, and the map is never iterated, so its layout cannot reach a result. One instance
 * per thread ({@code JobScratch}).
 *
 * <p>A search that expands {@code cap} nodes without reaching the goal returns {@link PathStatus#PARTIAL} to the
 * expanded node with the least h (then g, then id); one that runs out of nodes returns {@link PathStatus#NO_PATH} to
 * that node. Either is {@code NO_PATH} with no path when that node is the start.
 */
@HotPath
public final class GridAStar {
    static final double SQRT2 = Math.sqrt(2.0);
    private static final int INITIAL = 4_096;

    /** What a search found: the nodes from the start to the end, the end's cost, and the expansions. */
    public static final class Result {
        private final PathStatus status;
        private final int[] nodes;
        private final double cost;
        private final int expansions;

        Result(PathStatus status, int[] nodes, double cost, int expansions) {
            this.status = status;
            this.nodes = nodes;
            this.cost = cost;
            this.expansions = expansions;
        }

        public PathStatus status() {
            return status;
        }

        /** The path's nodes, start first; the result's own array, not a copy. */
        public int[] nodes() {
            return nodes;
        }

        public double cost() {
            return cost;
        }

        public int expansions() {
            return expansions;
        }
    }

    private int[] node = new int[INITIAL];
    private int[] column = new int[INITIAL];
    private double[] g = new double[INITIAL];
    private double[] h = new double[INITIAL];
    private double[] f = new double[INITIAL];
    private int[] parent = new int[INITIAL];
    private int[] heapAt = new int[INITIAL];
    private boolean[] closed = new boolean[INITIAL];
    private int size;

    private int[] heap = new int[INITIAL];
    private int heapSize;

    private int[] keys = new int[INITIAL * 2];
    private int[] values = new int[INITIAL * 2];
    private int[] stamps = new int[INITIAL * 2];
    private int stamp;

    private int goalX;
    private int goalZ;

    /**
     * The cheapest path from node {@code from} to any surface of column ({@code goalX}, {@code goalZ}), expanding at
     * most {@code cap} nodes.
     */
    public Result search(SurfaceGrid grid, int from, int goalX, int goalZ, int cap) {
        this.goalX = goalX;
        this.goalZ = goalZ;
        size = 0;
        heapSize = 0;
        if (++stamp == 0) {
            Arrays.fill(stamps, 0);
            stamp = 1;
        }
        int start = slot(from, grid.nodeX(from), grid.nodeZ(from));
        g[start] = 0;
        f[start] = h[start];
        parent[start] = -1;
        push(start);
        int best = start;
        int expansions = 0;
        while (heapSize > 0) {
            int s = pop();
            closed[s] = true;
            expansions++;
            int x = column[s] >>> 16;
            int z = column[s] & 0xFFFF;
            if (x == goalX && z == goalZ) {
                return new Result(PathStatus.OK, path(s), g[s], expansions);
            }
            if (better(s, best)) {
                best = s;
            }
            if (expansions >= cap) {
                return end(PathStatus.PARTIAL, best, start, expansions);
            }
            int layer = SurfaceGrid.nodeLayer(node[s]);
            int here = grid.surface(x, z, layer);
            for (int dir = 0; dir < Dir.COUNT; dir++) {
                int nl = grid.neighbour(x, z, layer, dir);
                if (nl < 0) {
                    continue;
                }
                int nx = x + Dir.dx(dir);
                int nz = z + Dir.dz(dir);
                int n = slot(grid.node(nx, nz, nl), nx, nz);
                if (closed[n]) {
                    continue;
                }
                double ng = g[s] + step(here, grid.surface(nx, nz, nl), dir);
                if (heapAt[n] < 0 && parent[n] == -2) { // discovered just now
                    g[n] = ng;
                    f[n] = ng + h[n];
                    parent[n] = s;
                    push(n);
                } else if (ng < g[n]) {
                    g[n] = ng;
                    f[n] = ng + h[n];
                    parent[n] = s;
                    up(heapAt[n]);
                }
            }
        }
        return end(PathStatus.NO_PATH, best, start, expansions);
    }

    /** The cost of a step in direction {@code dir} from surface {@code from} onto surface {@code to}. */
    static double step(int from, int to, int dir) {
        double factor = switch (Surface.kind(to)) {
            case Surface.SHALLOW_WATER -> 4.0;
            case Surface.DOOR -> 1.5;
            case Surface.SLOW -> 2.0;
            default -> 1.0;
        };
        double cost = (Dir.diagonal(dir) ? SQRT2 : 1.0) * factor;
        if (Surface.floor(to) - Surface.floor(from) >= 4) {
            cost += 0.5;
        }
        return cost + 0.1 * Surface.cost(to);
    }

    /** The octile distance between two columns. */
    static double octile(int x0, int z0, int x1, int z1) {
        int dx = Math.abs(x1 - x0);
        int dz = Math.abs(z1 - z0);
        return Math.max(dx, dz) + (SQRT2 - 1.0) * Math.min(dx, dz);
    }

    private Result end(PathStatus status, int best, int start, int expansions) {
        if (best == start) {
            return new Result(PathStatus.NO_PATH, new int[0], 0, expansions);
        }
        return new Result(status, path(best), g[best], expansions);
    }

    /** Least h, then least g, then the lower node id. */
    private boolean better(int a, int b) {
        if (h[a] != h[b]) {
            return h[a] < h[b];
        }
        if (g[a] != g[b]) {
            return g[a] < g[b];
        }
        return node[a] < node[b];
    }

    private int[] path(int end) {
        int length = 0;
        for (int s = end; s >= 0; s = parent[s]) {
            length++;
        }
        int[] nodes = new int[length];
        for (int s = end, i = length - 1; s >= 0; s = parent[s], i--) {
            nodes[i] = node[s];
        }
        return nodes;
    }

    /** The slot of node {@code id} in this search, made on first sight with parent -2 and its heuristic. */
    private int slot(int id, int x, int z) {
        int mask = keys.length - 1;
        int i = mix(id) & mask;
        while (stamps[i] == stamp) {
            if (keys[i] == id) {
                return values[i];
            }
            i = (i + 1) & mask;
        }
        if (size == node.length) {
            grow();
            return slot(id, x, z);
        }
        int s = size++;
        keys[i] = id;
        values[i] = s;
        stamps[i] = stamp;
        node[s] = id;
        column[s] = x << 16 | z;
        g[s] = Double.POSITIVE_INFINITY;
        h[s] = octile(x, z, goalX, goalZ);
        parent[s] = -2;
        heapAt[s] = -1;
        closed[s] = false;
        return s;
    }

    /** Doubles the slot arrays and rebuilds the map at twice their length, keeping it at most half full. */
    private void grow() {
        int capacity = node.length * 2;
        node = Arrays.copyOf(node, capacity);
        column = Arrays.copyOf(column, capacity);
        g = Arrays.copyOf(g, capacity);
        h = Arrays.copyOf(h, capacity);
        f = Arrays.copyOf(f, capacity);
        parent = Arrays.copyOf(parent, capacity);
        heapAt = Arrays.copyOf(heapAt, capacity);
        closed = Arrays.copyOf(closed, capacity);
        heap = Arrays.copyOf(heap, capacity);
        keys = new int[capacity * 2];
        values = new int[capacity * 2];
        stamps = new int[capacity * 2];
        int mask = keys.length - 1;
        for (int s = 0; s < size; s++) {
            int i = mix(node[s]) & mask;
            while (stamps[i] == stamp) {
                i = (i + 1) & mask;
            }
            keys[i] = node[s];
            values[i] = s;
            stamps[i] = stamp;
        }
    }

    private static int mix(int id) {
        int x = id * 0x9E3779B9;
        return x ^ (x >>> 16);
    }

    private boolean less(int a, int b) {
        if (f[a] != f[b]) {
            return f[a] < f[b];
        }
        if (h[a] != h[b]) {
            return h[a] < h[b];
        }
        return node[a] < node[b];
    }

    private void push(int s) {
        heap[heapSize] = s;
        heapAt[s] = heapSize;
        up(heapSize++);
    }

    private int pop() {
        int top = heap[0];
        heapAt[top] = -1;
        int last = heap[--heapSize];
        if (heapSize > 0) {
            heap[0] = last;
            heapAt[last] = 0;
            down(0);
        }
        return top;
    }

    private void up(int i) {
        int s = heap[i];
        while (i > 0) {
            int p = (i - 1) >>> 1;
            int ps = heap[p];
            if (!less(s, ps)) {
                break;
            }
            heap[i] = ps;
            heapAt[ps] = i;
            i = p;
        }
        heap[i] = s;
        heapAt[s] = i;
    }

    private void down(int i) {
        int s = heap[i];
        while (true) {
            int c = 2 * i + 1;
            if (c >= heapSize) {
                break;
            }
            if (c + 1 < heapSize && less(heap[c + 1], heap[c])) {
                c++;
            }
            if (!less(heap[c], s)) {
                break;
            }
            heap[i] = heap[c];
            heapAt[heap[c]] = i;
            i = c;
        }
        heap[i] = s;
        heapAt[s] = i;
    }
}
