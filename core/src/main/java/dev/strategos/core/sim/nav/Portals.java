package dev.strategos.core.sim.nav;

import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * HPA*'s abstract graph over a grid (PATH-08, 04 §9.3 stage 2). On each border between two pages, the crossings (a
 * surface linked to one across the border, in either direction) form entrances: runs along the border whose
 * surfaces are linked both ways to their neighbours in the run, on both sides. An entrance gets a portal at every
 * {@value #SPACING}th crossing and at its end; a portal is a pair of surfaces, one each side, with the step's cost each
 * way (infinite where there is no link that way). A page's portal surfaces are its abstract nodes, and the
 * cheapest cost between each two of them inside the page comes from a Dijkstra over the page's own links.
 *
 * <p>Immutable, owned by its {@link SurfaceGrid}. A grid that publishes pages recomputes the borders of those pages
 * and the abstract nodes of those pages and the four next to each.
 */
final class Portals {
    static final double NONE = Double.POSITIVE_INFINITY;

    private static final int SLOTS = SurfacePage.COLUMNS * SurfacePage.MAX_LAYERS;

    /** A page's east or south border: its portals, the near surface {@code a}, the far {@code b}. */
    private static final class Border {
        final int[] a;
        final int[] b;
        final double[] ab;
        final double[] ba;

        Border(int[] a, int[] b, double[] ab, double[] ba) {
            this.a = a;
            this.b = b;
            this.ab = ab;
            this.ba = ba;
        }
    }

    /** One page's abstract nodes: their surfaces, the costs between them inside the page, their crossings. */
    static final class Node {
        final int[] nodes;
        final double[] cost;
        final int[] crossStart;
        final int[] crossNode;
        final double[] crossCost;

        Node(int[] nodes, double[] cost, int[] crossStart, int[] crossNode, double[] crossCost) {
            this.nodes = nodes;
            this.cost = cost;
            this.crossStart = crossStart;
            this.crossNode = crossNode;
            this.crossCost = crossCost;
        }

        /** The index of grid node {@code node} among the page's abstract nodes, or -1. */
        int indexOf(int node) {
            int i = Arrays.binarySearch(nodes, node);
            return i >= 0 ? i : -1;
        }
    }

    private static final Border EMPTY_BORDER = new Border(new int[0], new int[0], new double[0], new double[0]);

    private final Border[] east;
    private final Border[] south;
    private final Node[] page;

    private Portals(Border[] east, Border[] south, Node[] page) {
        this.east = east;
        this.south = south;
        this.page = page;
    }

    /** Every border and page computed afresh. */
    static Portals of(SurfaceGrid grid) {
        int pages = grid.pagesX() * grid.pagesZ();
        Border[] east = new Border[pages];
        Border[] south = new Border[pages];
        for (int p = 0; p < pages; p++) {
            east[p] = border(grid, p, true);
            south[p] = border(grid, p, false);
        }
        Node[] page = new Node[pages];
        Dijkstra dijkstra = new Dijkstra();
        for (int p = 0; p < pages; p++) {
            page[p] = node(grid, east, south, p, dijkstra, null);
        }
        return new Portals(east, south, page);
    }

    /** This graph after {@code changed} pages of {@code grid} were published. */
    Portals update(SurfaceGrid grid, int[] changed) {
        Border[] nextEast = east.clone();
        Border[] nextSouth = south.clone();
        Node[] nextPage = page.clone();
        boolean[] redo = new boolean[page.length];
        for (int p : changed) {
            int px = p % grid.pagesX();
            int pz = p / grid.pagesX();
            nextEast[p] = border(grid, p, true);
            nextSouth[p] = border(grid, p, false);
            if (px > 0) {
                nextEast[p - 1] = border(grid, p - 1, true);
            }
            if (pz > 0) {
                nextSouth[p - grid.pagesX()] = border(grid, p - grid.pagesX(), false);
            }
            redo[p] = true;
            if (px > 0) {
                redo[p - 1] = true;
            }
            if (px + 1 < grid.pagesX()) {
                redo[p + 1] = true;
            }
            if (pz > 0) {
                redo[p - grid.pagesX()] = true;
            }
            if (pz + 1 < grid.pagesZ()) {
                redo[p + grid.pagesX()] = true;
            }
        }
        boolean[] rebuilt = new boolean[page.length];
        for (int p : changed) {
            rebuilt[p] = true;
        }
        Dijkstra dijkstra = new Dijkstra();
        for (int p = 0; p < page.length; p++) {
            if (redo[p]) {
                // a neighbour whose own surfaces and portals are as they were keeps its costs
                nextPage[p] = node(grid, nextEast, nextSouth, p, dijkstra, rebuilt[p] ? null : page[p]);
            }
        }
        return new Portals(nextEast, nextSouth, nextPage);
    }

    /** Page {@code p}'s abstract nodes (index {@code z * pagesX + x}). */
    Node page(int p) {
        return page[p];
    }

    /** The memory of the borders and pages, in bytes (budget G). */
    long bytes() {
        long total = 0;
        for (int p = 0; p < page.length; p++) {
            Node n = page[p];
            total += 4L * (n.nodes.length + n.crossStart.length + n.crossNode.length)
                    + 8L * (n.cost.length + n.crossCost.length);
            total += 24L * (east[p].a.length + south[p].a.length);
        }
        return total;
    }

    /** How many abstract nodes the graph has. */
    int count() {
        int count = 0;
        for (Node n : page) {
            count += n.nodes.length;
        }
        return count;
    }

    /** Page {@code p}'s east border ({@code east}) or south border: its entrances' portals. */
    private static Border border(SurfaceGrid grid, int p, boolean east) {
        int px = p % grid.pagesX();
        int pz = p / grid.pagesX();
        if (east ? px + 1 >= grid.pagesX() : pz + 1 >= grid.pagesZ()) {
            return EMPTY_BORDER;
        }
        int toward = east ? Dir.E : Dir.S;
        int back = Dir.opposite(toward);
        int along = east ? Dir.S : Dir.E;
        int[] a = new int[16];
        int[] b = new int[16];
        double[] ab = new double[16];
        double[] ba = new double[16];
        int n = 0;
        // open runs: the last crossing of each, as (near layer, far layer), and the run's crossings so far
        int[][] runs = new int[SurfacePage.MAX_LAYERS * SurfacePage.MAX_LAYERS][];
        int[] runLength = new int[runs.length];
        int[] runNear = new int[runs.length];
        int[] runFar = new int[runs.length];
        int open = 0;
        for (int i = 0; i <= VoxelSlice.SIDE; i++) {
            int x = east ? px * 16 + 15 : px * 16 + i;
            int z = east ? pz * 16 + i : pz * 16 + 15;
            int fx = x + Dir.dx(toward);
            int fz = z + Dir.dz(toward);
            boolean inside = i < VoxelSlice.SIDE && grid.inside(x, z) && grid.inside(fx, fz);
            int[][] nextRuns = new int[runs.length][];
            int[] nextLength = new int[runs.length];
            int[] nextNear = new int[runs.length];
            int[] nextFar = new int[runs.length];
            int nextOpen = 0;
            boolean[] taken = new boolean[runs.length];
            if (inside) {
                for (int near = 0; near < grid.count(x, z); near++) {
                    for (int far = 0; far < grid.count(fx, fz); far++) {
                        boolean there = grid.neighbour(x, z, near, toward) == far;
                        boolean backHere = grid.neighbour(fx, fz, far, back) == near;
                        if (!there && !backHere) {
                            continue;
                        }
                        int crossing = near * SurfacePage.MAX_LAYERS + far;
                        int run = -1;
                        for (int r = 0; r < open; r++) {
                            if (!taken[r]
                                    && joined(grid, x - Dir.dx(along), z - Dir.dz(along), runNear[r], near, along)
                                    && joined(grid, fx - Dir.dx(along), fz - Dir.dz(along), runFar[r], far, along)) {
                                run = r;
                                break;
                            }
                        }
                        int[] crossings;
                        int length;
                        if (run >= 0) {
                            taken[run] = true;
                            crossings = runs[run];
                            length = runLength[run];
                        } else {
                            crossings = new int[VoxelSlice.SIDE];
                            length = 0;
                        }
                        crossings[length++] = i * 16 + crossing;
                        nextRuns[nextOpen] = crossings;
                        nextLength[nextOpen] = length;
                        nextNear[nextOpen] = near;
                        nextFar[nextOpen] = far;
                        nextOpen++;
                    }
                }
            }
            for (int r = 0; r < open; r++) {
                if (!taken[r]) {
                    int length = runLength[r];
                    int[] picks = picks(length);
                    for (int pick : picks) {
                        int code = runs[r][pick];
                        int at = code / 16;
                        int near = (code % 16) / SurfacePage.MAX_LAYERS;
                        int far = code % SurfacePage.MAX_LAYERS;
                        int cx = east ? px * 16 + 15 : px * 16 + at;
                        int cz = east ? pz * 16 + at : pz * 16 + 15;
                        int cfx = cx + Dir.dx(toward);
                        int cfz = cz + Dir.dz(toward);
                        if (n == a.length) {
                            a = Arrays.copyOf(a, n * 2);
                            b = Arrays.copyOf(b, n * 2);
                            ab = Arrays.copyOf(ab, n * 2);
                            ba = Arrays.copyOf(ba, n * 2);
                        }
                        a[n] = grid.node(cx, cz, near);
                        b[n] = grid.node(cfx, cfz, far);
                        ab[n] = grid.neighbour(cx, cz, near, toward) == far
                                ? GridAStar.step(grid.surface(cx, cz, near), grid.surface(cfx, cfz, far), toward)
                                : NONE;
                        ba[n] = grid.neighbour(cfx, cfz, far, back) == near
                                ? GridAStar.step(grid.surface(cfx, cfz, far), grid.surface(cx, cz, near), back)
                                : NONE;
                        n++;
                    }
                }
            }
            runs = nextRuns;
            runLength = nextLength;
            runNear = nextNear;
            runFar = nextFar;
            open = nextOpen;
        }
        return new Border(Arrays.copyOf(a, n), Arrays.copyOf(b, n), Arrays.copyOf(ab, n), Arrays.copyOf(ba, n));
    }

    /**
     * Portals stand this many crossings apart along an entrance, and at both its ends: on the proving ground this
     * keeps paths within 5 % of full A* and a crossing under 10 % of its expansions (spec §1.6 D row 48).
     */
    static final int SPACING = 5;

    /** The crossings of an entrance of {@code length} that become portals: every {@link #SPACING}th and the last. */
    private static int[] picks(int length) {
        int[] picks = new int[length / SPACING + 2];
        int n = 0;
        for (int i = 0; i < length; i += SPACING) {
            picks[n++] = i;
        }
        if (picks[n - 1] != length - 1) {
            picks[n++] = length - 1;
        }
        return Arrays.copyOf(picks, n);
    }

    /** Whether the surface at ({@code x}, {@code z}, {@code layer}) and the next along {@code along} link both ways. */
    private static boolean joined(SurfaceGrid grid, int x, int z, int layer, int nextLayer, int along) {
        return grid.neighbour(x, z, layer, along) == nextLayer
                && grid.neighbour(x + Dir.dx(along), z + Dir.dz(along), nextLayer, Dir.opposite(along)) == layer;
    }

    /**
     * Page {@code p}'s abstract nodes from its four borders, with their costs and crossings; the costs are {@code
     * same}'s when the page did not change and its nodes are the same.
     */
    private static Node node(
            SurfaceGrid grid, Border[] east, Border[] south, int p, Dijkstra dijkstra, @Nullable Node same) {
        int px = p % grid.pagesX();
        int pz = p / grid.pagesX();
        int[] nodes = new int[16];
        int n = 0;
        Border[] near = {east[p], south[p]};
        for (Border border : near) {
            for (int node : border.a) {
                nodes = append(nodes, n++, node);
            }
        }
        Border west = px > 0 ? east[p - 1] : EMPTY_BORDER;
        Border north = pz > 0 ? south[p - grid.pagesX()] : EMPTY_BORDER;
        for (Border border : new Border[] {west, north}) {
            for (int node : border.b) {
                nodes = append(nodes, n++, node);
            }
        }
        Arrays.sort(nodes, 0, n);
        int distinct = 0;
        for (int i = 0; i < n; i++) {
            if (distinct == 0 || nodes[distinct - 1] != nodes[i]) {
                nodes[distinct++] = nodes[i];
            }
        }
        nodes = Arrays.copyOf(nodes, distinct);
        int k = nodes.length;
        double[] cost;
        if (same != null && Arrays.equals(same.nodes, nodes)) {
            cost = same.cost;
        } else {
            cost = new double[k * k];
            for (int i = 0; i < k; i++) {
                dijkstra.from(grid, px, pz, nodes[i]);
                for (int j = 0; j < k; j++) {
                    cost[i * k + j] = dijkstra.distance(grid, nodes[j]);
                }
            }
        }
        int[] crossCount = new int[k];
        int[][] partner = new int[k][];
        double[][] partnerCost = new double[k][];
        for (int i = 0; i < k; i++) {
            partner[i] = new int[2];
            partnerCost[i] = new double[2];
        }
        for (Border border : near) {
            for (int i = 0; i < border.a.length; i++) {
                if (border.ab[i] < NONE) {
                    int at = Arrays.binarySearch(nodes, border.a[i]);
                    addPartner(partner, partnerCost, crossCount, at, border.b[i], border.ab[i]);
                }
            }
        }
        for (Border border : new Border[] {west, north}) {
            for (int i = 0; i < border.b.length; i++) {
                if (border.ba[i] < NONE) {
                    int at = Arrays.binarySearch(nodes, border.b[i]);
                    addPartner(partner, partnerCost, crossCount, at, border.a[i], border.ba[i]);
                }
            }
        }
        int[] crossStart = new int[k + 1];
        for (int i = 0; i < k; i++) {
            crossStart[i + 1] = crossStart[i] + crossCount[i];
        }
        int[] crossNode = new int[crossStart[k]];
        double[] crossCost = new double[crossStart[k]];
        for (int i = 0; i < k; i++) {
            System.arraycopy(partner[i], 0, crossNode, crossStart[i], crossCount[i]);
            System.arraycopy(partnerCost[i], 0, crossCost, crossStart[i], crossCount[i]);
        }
        return new Node(nodes, cost, crossStart, crossNode, crossCost);
    }

    private static void addPartner(
            int[][] partner, double[][] partnerCost, int[] count, int at, int node, double cost) {
        if (count[at] == partner[at].length) {
            partner[at] = Arrays.copyOf(partner[at], count[at] * 2);
            partnerCost[at] = Arrays.copyOf(partnerCost[at], count[at] * 2);
        }
        partner[at][count[at]] = node;
        partnerCost[at][count[at]] = cost;
        count[at]++;
    }

    private static int[] append(int[] values, int at, int value) {
        int[] grown = at == values.length ? Arrays.copyOf(values, at * 2) : values;
        grown[at] = value;
        return grown;
    }

    /**
     * Dijkstra over one page's own links, by slot {@code column * 4 + layer}: from one surface forward, or, for a
     * goal, backward from several. Ties pop the lower slot first. Reused: one per thread.
     */
    static final class Dijkstra {
        private final double[] dist = new double[SLOTS];
        private final int[] heap = new int[SLOTS];
        private final int[] heapAt = new int[SLOTS];
        private final int[] touched = new int[SLOTS];
        private int touchedCount;
        private int heapSize;
        private int pageX;
        private int pageZ;
        private int limitX;
        private int limitZ;
        private int popped;

        Dijkstra() {
            Arrays.fill(dist, NONE);
            Arrays.fill(heapAt, -1);
        }

        /** The costs from grid node {@code from} to every surface of its page, inside the page. */
        void from(SurfaceGrid grid, int pageX, int pageZ, int from) {
            start(pageX, pageZ);
            relax(slot(grid, from), 0);
            run(grid, false);
        }

        /** The costs from every surface of page ({@code pageX}, {@code pageZ}) to the nearest of {@code goals}. */
        void to(SurfaceGrid grid, int pageX, int pageZ, int[] goals) {
            start(pageX, pageZ);
            for (int goal : goals) {
                relax(slot(grid, goal), 0);
            }
            run(grid, true);
        }

        /** The cost found to (or from) grid node {@code node}, which must be in the page; {@link #NONE} if none. */
        double distance(SurfaceGrid grid, int node) {
            return dist[slot(grid, node)];
        }

        private int slot(SurfaceGrid grid, int node) {
            return SurfacePage.column(grid.nodeX(node) & 15, grid.nodeZ(node) & 15) * SurfacePage.MAX_LAYERS
                    + SurfaceGrid.nodeLayer(node);
        }

        /** Surfaces settled by the last run, counted as expansions. */
        int popped() {
            return popped;
        }

        private void start(int pageX, int pageZ) {
            this.pageX = pageX;
            this.pageZ = pageZ;
            popped = 0;
            for (int i = 0; i < touchedCount; i++) {
                dist[touched[i]] = NONE;
                heapAt[touched[i]] = -1;
            }
            touchedCount = 0;
            heapSize = 0;
        }

        private void run(SurfaceGrid grid, boolean backward) {
            SurfacePage page = grid.page(pageX, pageZ);
            limitX = Math.min(VoxelSlice.SIDE, grid.sizeX() - pageX * VoxelSlice.SIDE);
            limitZ = Math.min(VoxelSlice.SIDE, grid.sizeZ() - pageZ * VoxelSlice.SIDE);
            while (heapSize > 0) {
                int s = pop();
                popped++;
                int column = s / SurfacePage.MAX_LAYERS;
                int layer = s % SurfacePage.MAX_LAYERS;
                int x = column / 16;
                int z = column % 16;
                int here = page.surface(column, layer);
                for (int dir = 0; dir < Dir.COUNT; dir++) {
                    int nx = x + Dir.dx(dir);
                    int nz = z + Dir.dz(dir);
                    if (nx < 0 || nz < 0 || nx >= limitX || nz >= limitZ) {
                        continue; // outside the page, or past the match's edge
                    }
                    int nc = SurfacePage.column(nx, nz);
                    if (!backward) {
                        int nl = page.target(column, layer, dir);
                        if (nl >= 0) {
                            relax(
                                    nc * SurfacePage.MAX_LAYERS + nl,
                                    dist[s] + GridAStar.step(here, page.surface(nc, nl), dir));
                        }
                    } else {
                        int into = Dir.opposite(dir);
                        for (int nl = 0; nl < page.count(nc); nl++) {
                            if (page.target(nc, nl, into) == layer) {
                                relax(
                                        nc * SurfacePage.MAX_LAYERS + nl,
                                        dist[s] + GridAStar.step(page.surface(nc, nl), here, into));
                            }
                        }
                    }
                }
            }
        }

        private void relax(int s, double d) {
            if (d >= dist[s]) {
                return;
            }
            if (dist[s] == NONE) {
                touched[touchedCount++] = s;
            }
            dist[s] = d;
            if (heapAt[s] < 0) {
                heap[heapSize] = s;
                heapAt[s] = heapSize;
                up(heapSize++);
            } else {
                up(heapAt[s]);
            }
        }

        private boolean less(int a, int b) {
            return dist[a] < dist[b] || (dist[a] == dist[b] && a < b);
        }

        private int pop() {
            int top = heap[0];
            heapAt[top] = -2;
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
                int parent = (i - 1) >>> 1;
                if (!less(s, heap[parent])) {
                    break;
                }
                heap[i] = heap[parent];
                heapAt[heap[i]] = i;
                i = parent;
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
                heapAt[heap[i]] = i;
                i = c;
            }
            heap[i] = s;
            heapAt[s] = i;
        }
    }
}
