package dev.strategos.core.sim.nav;

import dev.strategos.core.shared.HotPath;
import java.util.Arrays;
import java.util.BitSet;
import org.jspecify.annotations.Nullable;

/**
 * A long path in two levels (PATH-08, HPA*, 04 §9.3 stage 2). The start joins its page's portals by a Dijkstra inside
 * the page, the goal column (its reachable surfaces) by one backward inside its page; A* over the portal graph, with
 * the octile heuristic and at most {@value #ABSTRACT_CAP} expansions, gives a chain of portals; each step of the chain
 * inside a page is then searched for in that page alone, and a step across a border is that border's link. The
 * result costs what the abstract chain cost, and every expansion counts: the Dijkstras', the abstract search's and
 * the refinements'. At the abstract cap the path is {@code PARTIAL}, to the expanded portal nearest the goal (least
 * h, then g, then id). One instance per thread.
 */
@HotPath
final class HierarchicalSearch {
    static final int ABSTRACT_CAP = 4_000;

    private static final int START = -1;
    private static final int GOAL = -2;

    private final Portals.Dijkstra dijkstra = new Portals.Dijkstra();
    private double[] startCost = new double[64];
    private double[] goalCost = new double[64];

    private int[] node = new int[1_024];
    private double[] g = new double[1_024];
    private double[] f = new double[1_024];
    private double[] h = new double[1_024];
    private int[] parent = new int[1_024];
    private int[] heapAt = new int[1_024];
    private int[] heap = new int[1_024];
    private int size;
    private int heapSize;
    private int[] keys = new int[2_048];
    private int[] values = new int[2_048];
    private int[] stamps = new int[2_048];
    private int stamp;

    /**
     * The path from {@code from} to column ({@code goalX}, {@code goalZ}), whose surfaces in {@code reachable}
     * components are the goal; null when the portal graph finds none, so the caller searches flat.
     */
    GridAStar.@Nullable Result search(
            SurfaceGrid grid, GridAStar low, int from, int goalX, int goalZ, BitSet reachable, int cap) {
        Portals portals = grid.portals();
        int fromPage = page(grid, grid.nodeX(from), grid.nodeZ(from));
        int goalPage = page(grid, goalX, goalZ);
        if (fromPage == goalPage) {
            return null;
        }
        Portals.Node startPage = portals.page(fromPage);
        Portals.Node endPage = portals.page(goalPage);
        if (startCost.length < startPage.nodes.length) {
            startCost = new double[startPage.nodes.length];
        }
        if (goalCost.length < endPage.nodes.length) {
            goalCost = new double[endPage.nodes.length];
        }
        int expansions = 0;

        dijkstra.from(grid, fromPage % grid.pagesX(), fromPage / grid.pagesX(), from);
        expansions += dijkstra.popped();
        for (int i = 0; i < startPage.nodes.length; i++) {
            startCost[i] = dijkstra.distance(grid, startPage.nodes[i]);
        }
        int[] goals = goalSurfaces(grid, goalX, goalZ, reachable);
        if (goals.length == 0) {
            return null;
        }
        dijkstra.to(grid, goalPage % grid.pagesX(), goalPage / grid.pagesX(), goals);
        expansions += dijkstra.popped();
        for (int i = 0; i < endPage.nodes.length; i++) {
            goalCost[i] = dijkstra.distance(grid, endPage.nodes[i]);
        }

        // A* over the portals; START and GOAL are virtual nodes
        size = 0;
        heapSize = 0;
        if (++stamp == 0) {
            Arrays.fill(stamps, 0);
            stamp = 1;
        }
        int startSlot = slot(START, GridAStar.octile(grid.nodeX(from), grid.nodeZ(from), goalX, goalZ));
        g[startSlot] = 0;
        f[startSlot] = h[startSlot];
        parent[startSlot] = -1;
        push(startSlot);
        int abstractExpansions = 0;
        int found = -1;
        int best = startSlot;
        while (heapSize > 0) {
            int s = pop();
            abstractExpansions++;
            if (node[s] == GOAL) {
                found = s;
                break;
            }
            if (h[s] < h[best] || (h[s] == h[best] && (g[s] < g[best] || (g[s] == g[best] && node[s] < node[best])))) {
                best = s;
            }
            if (abstractExpansions >= ABSTRACT_CAP) {
                break;
            }
            if (node[s] == START) {
                for (int i = 0; i < startPage.nodes.length; i++) {
                    relax(grid, s, startPage.nodes[i], startCost[i], goalX, goalZ);
                }
                continue;
            }
            int at = node[s];
            int p = page(grid, grid.nodeX(at), grid.nodeZ(at));
            Portals.Node here = portals.page(p);
            int i = here.indexOf(at);
            int k = here.nodes.length;
            for (int j = 0; j < k; j++) {
                double c = here.cost[i * k + j];
                if (j != i && c < Portals.NONE) {
                    relax(grid, s, here.nodes[j], c, goalX, goalZ);
                }
            }
            for (int e = here.crossStart[i]; e < here.crossStart[i + 1]; e++) {
                relax(grid, s, here.crossNode[e], here.crossCost[e], goalX, goalZ);
            }
            if (p == goalPage && goalCost[i] < Portals.NONE) {
                relax(grid, s, GOAL, goalCost[i], goalX, goalZ);
            }
        }
        expansions += abstractExpansions;
        PathStatus status = PathStatus.OK;
        if (found < 0) {
            if (abstractExpansions < ABSTRACT_CAP || best == startSlot) {
                return null; // no chain of portals at all: the flat search decides
            }
            found = best; // the cap: a partial path to the expanded portal nearest the goal
            status = PathStatus.PARTIAL;
        }

        // the chain, START first
        int length = 0;
        for (int s = found; s >= 0; s = parent[s]) {
            length++;
        }
        int[] chain = new int[length];
        for (int s = found, i = length - 1; s >= 0; s = parent[s], i--) {
            chain[i] = node[s];
        }

        // refinement, step by step
        int[] path = new int[64];
        int n = 0;
        path[n++] = from;
        double cost = 0;
        int at = from;
        for (int c = 1; c < chain.length; c++) {
            int next = chain[c];
            int pageOfAt = page(grid, grid.nodeX(at), grid.nodeZ(at));
            GridAStar.Result step;
            if (next == GOAL) {
                step = low.searchInPage(
                        grid, at, goalX, goalZ, pageOfAt % grid.pagesX(), pageOfAt / grid.pagesX(), cap);
            } else if (page(grid, grid.nodeX(next), grid.nodeZ(next)) != pageOfAt) {
                path = append(path, n++, next); // across the border: one link
                cost += crossCost(grid, portals, at, next);
                at = next;
                continue;
            } else if (next == at) {
                continue;
            } else {
                step = low.searchInPage(grid, at, next, pageOfAt % grid.pagesX(), pageOfAt / grid.pagesX(), cap);
            }
            expansions += step.expansions();
            if (step.status() != PathStatus.OK) {
                return null;
            }
            int[] nodes = step.nodes();
            for (int i = 1; i < nodes.length; i++) {
                path = append(path, n++, nodes[i]);
            }
            cost += step.cost();
            at = nodes[nodes.length - 1];
        }
        return new GridAStar.Result(status, Arrays.copyOf(path, n), cost, expansions);
    }

    /** The goal column's surfaces in {@code reachable} components, as grid nodes. */
    private static int[] goalSurfaces(SurfaceGrid grid, int x, int z, BitSet reachable) {
        int count = grid.count(x, z);
        int[] goals = new int[count];
        int n = 0;
        for (int layer = 0; layer < count; layer++) {
            int node = grid.node(x, z, layer);
            if (reachable.get(grid.component(node))) {
                goals[n++] = node;
            }
        }
        return Arrays.copyOf(goals, n);
    }

    private static double crossCost(SurfaceGrid grid, Portals portals, int from, int to) {
        Portals.Node here = portals.page(page(grid, grid.nodeX(from), grid.nodeZ(from)));
        int i = here.indexOf(from);
        for (int e = here.crossStart[i]; e < here.crossStart[i + 1]; e++) {
            if (here.crossNode[e] == to) {
                return here.crossCost[e];
            }
        }
        throw new IllegalStateException("no crossing from " + from + " to " + to);
    }

    private static int page(SurfaceGrid grid, int x, int z) {
        return (z >> 4) * grid.pagesX() + (x >> 4);
    }

    private void relax(SurfaceGrid grid, int from, int to, double cost, int goalX, int goalZ) {
        double hn = to == GOAL ? 0 : GridAStar.octile(grid.nodeX(to), grid.nodeZ(to), goalX, goalZ);
        int s = slot(to, hn);
        if (heapAt[s] == -2) {
            return; // closed
        }
        double ng = g[from] + cost;
        if (heapAt[s] == -1) {
            g[s] = ng;
            f[s] = ng + h[s];
            parent[s] = from;
            push(s);
        } else if (ng < g[s]) {
            g[s] = ng;
            f[s] = ng + h[s];
            parent[s] = from;
            up(heapAt[s]);
        }
    }

    /** The slot of abstract node {@code id}, made on first sight with its heuristic. */
    private int slot(int id, double heuristic) {
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
            return slot(id, heuristic);
        }
        int s = size++;
        keys[i] = id;
        values[i] = s;
        stamps[i] = stamp;
        node[s] = id;
        g[s] = Double.POSITIVE_INFINITY;
        h[s] = heuristic;
        parent[s] = -1;
        heapAt[s] = -1;
        return s;
    }

    private void grow() {
        int capacity = node.length * 2;
        node = Arrays.copyOf(node, capacity);
        g = Arrays.copyOf(g, capacity);
        f = Arrays.copyOf(f, capacity);
        h = Arrays.copyOf(h, capacity);
        parent = Arrays.copyOf(parent, capacity);
        heapAt = Arrays.copyOf(heapAt, capacity);
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

    private static int[] append(int[] values, int at, int value) {
        int[] grown = at == values.length ? Arrays.copyOf(values, at * 2) : values;
        grown[at] = value;
        return grown;
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
            int p = (i - 1) >>> 1;
            if (!less(s, heap[p])) {
                break;
            }
            heap[i] = heap[p];
            heapAt[heap[i]] = i;
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
            heapAt[heap[i]] = i;
            i = c;
        }
        heap[i] = s;
        heapAt[s] = i;
    }
}
