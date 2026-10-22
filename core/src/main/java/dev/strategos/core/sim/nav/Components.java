package dev.strategos.core.sim.nav;

import dev.strategos.core.shared.CoreApi;
import java.util.Arrays;
import java.util.BitSet;
import org.jspecify.annotations.Nullable;

/**
 * Reachability over a grid's surfaces (PATH-04, 04 §9.3, spec §5.5). Surfaces joined by links both ways form
 * union-find components: each page's local components, joined across page edges; a surface's component is one {@code
 * int}, and two surfaces with the same one reach each other. A link one way only, a drop of more than a step, joins
 * nothing: it is an edge from one component to another, and since drops only go down, these edges form a DAG. What a
 * unit can reach is its component and every component downstream of it ({@link #reachable}). Labels are the lowest
 * node of each component in page order, so equal grids give equal labels.
 *
 * <p>Immutable, owned by its {@link SurfaceGrid}. Per page it keeps the local labels (the page builder's, or, for a
 * page reaching past the match's edge, labels joined only through the match's own columns), a mask of the columns of
 * each local component, its one-way links between local components, and its distinct links into other pages. A grid
 * that publishes pages recomputes these for those pages and their eight neighbours only, then joins everything again:
 * a few thousand nodes for a 512² arena.
 */
@CoreApi
public final class Components {
    private static final int SLOTS = SurfacePage.COLUMNS * SurfacePage.MAX_LAYERS;
    private static final long ONE_WAY = 1L << 62;

    /** One page's local labels ({@code null} for the builder's own, else by {@code column * 4 + layer}). */
    private static final class Local {
        private final int @Nullable [] labels;
        private final int count;
        private final long[] masks;
        private final long[] drops;

        Local(int @Nullable [] labels, int count, long[] masks, long[] drops) {
            this.labels = labels;
            this.count = count;
            this.masks = masks;
            this.drops = drops;
        }
    }

    private final Local[] local;
    private final long[][] border;
    private final int[] first;
    private final int[] label;
    private final int[] edgeStart;
    private final int[] edges;

    private Components(Local[] local, long[][] border, int[] first, int[] label, int[] edgeStart, int[] edges) {
        this.local = local;
        this.border = border;
        this.first = first;
        this.label = label;
        this.edgeStart = edgeStart;
        this.edges = edges;
    }

    /** Every page's local labels and border links computed afresh. */
    static Components of(SurfaceGrid grid) {
        int pages = grid.pagesX() * grid.pagesZ();
        Local[] local = new Local[pages];
        for (int page = 0; page < pages; page++) {
            local[page] = local(grid, page);
        }
        long[][] border = new long[pages][];
        for (int page = 0; page < pages; page++) {
            border[page] = border(grid, local, page);
        }
        return join(local, border);
    }

    /** These components after {@code changed} pages of {@code grid} were published. */
    Components update(SurfaceGrid grid, int[] changed) {
        Local[] nextLocal = local.clone();
        for (int page : changed) {
            nextLocal[page] = local(grid, page);
        }
        long[][] next = border.clone();
        boolean[] redo = new boolean[next.length];
        for (int page : changed) {
            int px = page % grid.pagesX();
            int pz = page / grid.pagesX();
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int x = px + dx;
                    int z = pz + dz;
                    if (x >= 0 && z >= 0 && x < grid.pagesX() && z < grid.pagesZ()) {
                        redo[z * grid.pagesX() + x] = true;
                    }
                }
            }
        }
        for (int page = 0; page < next.length; page++) {
            if (redo[page]) {
                next[page] = border(grid, nextLocal, page);
            }
        }
        return join(nextLocal, next);
    }

    /** The component of the surface at {@code layer} of {@code column} in page {@code page} of {@code grid}. */
    int of(SurfaceGrid grid, int page, int column, int layer) {
        return label[first[page] + localOf(grid, local, page, column, layer)];
    }

    /** The components a unit in {@code component} can reach: its own and every one downstream by drops. */
    public BitSet reachable(int component) {
        BitSet seen = new BitSet(label.length);
        int[] open = new int[16];
        int n = 0;
        seen.set(component);
        open[n++] = component;
        while (n > 0) {
            int c = open[--n];
            for (int e = edgeStart[c]; e < edgeStart[c + 1]; e++) {
                int next = edges[e];
                if (!seen.get(next)) {
                    seen.set(next);
                    if (n == open.length) {
                        open = Arrays.copyOf(open, n * 2);
                    }
                    open[n++] = next;
                }
            }
        }
        return seen;
    }

    /** Whether any surface of page {@code page} (index {@code z * pagesX + x}) is in one of {@code components}. */
    public boolean pageHas(int page, BitSet components) {
        for (int g = first[page]; g < first[page + 1]; g++) {
            if (components.get(label[g])) {
                return true;
            }
        }
        return false;
    }

    /** Whether column {@code column} of page {@code page} holds a surface in one of {@code components}. */
    public boolean columnHas(int page, int column, BitSet components) {
        long[] masks = local[page].masks;
        for (int g = first[page]; g < first[page + 1]; g++) {
            if (components.get(label[g]) && (masks[(g - first[page]) * 4 + (column >> 6)] & (1L << column)) != 0) {
                return true;
            }
        }
        return false;
    }

    /** The memory of the labels, masks and links, in bytes (budget G). */
    long bytes() {
        long total = 4L * (first.length + label.length + edgeStart.length + edges.length);
        for (int page = 0; page < local.length; page++) {
            int[] labels = local[page].labels;
            total += 8L * (local[page].masks.length + border[page].length) + (labels == null ? 0 : 4L * labels.length);
        }
        return total;
    }

    /** How many components there are. */
    public int count() {
        int count = 0;
        for (int g = 0; g < label.length; g++) {
            if (label[g] == g) {
                count++;
            }
        }
        return count;
    }

    private static Components join(Local[] local, long[][] border) {
        int pages = border.length;
        int[] first = new int[pages + 1];
        for (int page = 0; page < pages; page++) {
            first[page + 1] = first[page] + local[page].count;
        }
        int[] parent = new int[first[pages]];
        for (int g = 0; g < parent.length; g++) {
            parent[g] = g;
        }
        for (int page = 0; page < pages; page++) {
            for (long link : border[page]) {
                if ((link & ONE_WAY) == 0) {
                    union(parent, first[page] + here(link), first[other(link)] + there(link));
                }
            }
        }
        for (int g = 0; g < parent.length; g++) {
            parent[g] = find(parent, g);
        }
        long[] pairs = new long[64];
        int n = 0;
        for (int page = 0; page < pages; page++) {
            for (long drop : local[page].drops) {
                int from = parent[first[page] + (int) (drop >>> 32)];
                int to = parent[first[page] + (int) drop];
                if (from != to) {
                    pairs = add(pairs, n++, (long) from << 32 | to);
                }
            }
            for (long link : border[page]) {
                if ((link & ONE_WAY) != 0) {
                    int from = parent[first[page] + here(link)];
                    int to = parent[first[other(link)] + there(link)];
                    if (from != to) {
                        pairs = add(pairs, n++, (long) from << 32 | to);
                    }
                }
            }
        }
        pairs = PageBuilder.distinct(pairs, n);
        int[] edgeStart = new int[parent.length + 1];
        for (long pair : pairs) {
            edgeStart[(int) (pair >>> 32) + 1]++;
        }
        for (int g = 0; g < parent.length; g++) {
            edgeStart[g + 1] += edgeStart[g];
        }
        int[] edges = new int[pairs.length];
        for (int i = 0; i < pairs.length; i++) {
            edges[i] = (int) pairs[i]; // sorted by source, so each source's targets lie together, in order
        }
        return new Components(local, border, first, parent, edgeStart, edges);
    }

    private static long[] add(long[] values, int at, long value) {
        long[] grown = at == values.length ? Arrays.copyOf(values, at * 2) : values;
        grown[at] = value;
        return grown;
    }

    /**
     * A page's local labels, column masks and one-way links: the builder's for a page inside the match; for one
     * reaching past its edge, labels joined only by two-way links between the match's own columns, which the builder
     * could not know.
     */
    private static Local local(SurfaceGrid grid, int page) {
        int px = page % grid.pagesX();
        int pz = page / grid.pagesX();
        SurfacePage here = grid.page(px, pz);
        boolean whole = (px + 1) * VoxelSlice.SIDE <= grid.sizeX() && (pz + 1) * VoxelSlice.SIDE <= grid.sizeZ();
        int[] labels = null;
        int count = here.components();
        long[] drops = here.drops();
        if (!whole) {
            int[] parent = new int[SLOTS];
            for (int i = 0; i < SLOTS; i++) {
                parent[i] = i;
            }
            long[] oneWay = new long[16];
            int n = 0;
            for (int column = 0; column < SurfacePage.COLUMNS; column++) {
                int lx = px * VoxelSlice.SIDE + column / 16;
                int lz = pz * VoxelSlice.SIDE + column % 16;
                for (int layer = 0; layer < grid.count(lx, lz); layer++) {
                    for (int dir = 0; dir < Dir.COUNT; dir++) {
                        int nx = lx + Dir.dx(dir);
                        int nz = lz + Dir.dz(dir);
                        if ((nx >> 4) != px || (nz >> 4) != pz) {
                            continue;
                        }
                        int nl = grid.neighbour(lx, lz, layer, dir);
                        if (nl < 0) {
                            continue;
                        }
                        int a = column * 4 + layer;
                        int b = SurfacePage.column(nx & 15, nz & 15) * 4 + nl;
                        if (grid.neighbour(nx, nz, nl, Dir.opposite(dir)) == layer) {
                            union(parent, a, b);
                        } else {
                            oneWay = add(oneWay, n++, (long) a << 32 | b);
                        }
                    }
                }
            }
            labels = new int[SLOTS];
            Arrays.fill(labels, -1);
            count = 0;
            for (int slot = 0; slot < SLOTS; slot++) {
                if (slot % 4 >= here.count(slot / 4)) {
                    continue;
                }
                int root = find(parent, slot);
                labels[slot] = root == slot ? count++ : labels[root];
            }
            for (int i = 0; i < n; i++) {
                int from = labels[(int) (oneWay[i] >>> 32)];
                int to = labels[(int) oneWay[i]];
                oneWay[i] = (long) from << 32 | to;
            }
            drops = PageBuilder.distinct(oneWay, n);
        }
        long[] masks = new long[count * 4];
        for (int column = 0; column < SurfacePage.COLUMNS; column++) {
            int lx = px * VoxelSlice.SIDE + column / 16;
            int lz = pz * VoxelSlice.SIDE + column % 16;
            for (int layer = 0; layer < grid.count(lx, lz); layer++) {
                int l = labels == null ? here.component(column, layer) : labels[column * 4 + layer];
                masks[l * 4 + (column >> 6)] |= 1L << column;
            }
        }
        return new Local(labels, count, masks, drops);
    }

    private static int localOf(SurfaceGrid grid, Local[] local, int page, int column, int layer) {
        int[] labels = local[page].labels;
        return labels == null
                ? grid.page(page % grid.pagesX(), page / grid.pagesX()).component(column, layer)
                : labels[column * 4 + layer];
    }

    /**
     * Page {@code page}'s distinct links from its edge columns into other pages, sorted: (local, other page, other
     * local), with {@link #ONE_WAY} set when the other surface does not link back.
     */
    private static long[] border(SurfaceGrid grid, Local[] local, int page) {
        int px = page % grid.pagesX();
        int pz = page / grid.pagesX();
        if (local[page].count == 0) {
            return new long[0];
        }
        long[] links = new long[64];
        int n = 0;
        for (int x = 0; x < VoxelSlice.SIDE; x++) {
            for (int z = 0; z < VoxelSlice.SIDE; z++) {
                if (x != 0 && z != 0 && x != VoxelSlice.SIDE - 1 && z != VoxelSlice.SIDE - 1) {
                    continue;
                }
                int lx = px * VoxelSlice.SIDE + x;
                int lz = pz * VoxelSlice.SIDE + z;
                int count = grid.count(lx, lz);
                for (int layer = 0; layer < count; layer++) {
                    int here = localOf(grid, local, page, SurfacePage.column(x, z), layer);
                    for (int dir = 0; dir < Dir.COUNT; dir++) {
                        int nx = lx + Dir.dx(dir);
                        int nz = lz + Dir.dz(dir);
                        if ((nx >> 4) == px && (nz >> 4) == pz) {
                            continue;
                        }
                        int nl = grid.neighbour(lx, lz, layer, dir);
                        if (nl < 0) {
                            continue;
                        }
                        int other = (nz >> 4) * grid.pagesX() + (nx >> 4);
                        int there = localOf(grid, local, other, SurfacePage.column(nx & 15, nz & 15), nl);
                        long oneWay = grid.neighbour(nx, nz, nl, Dir.opposite(dir)) == layer ? 0 : ONE_WAY;
                        links = add(links, n++, oneWay | (long) here << 40 | (long) other << 16 | there);
                    }
                }
            }
        }
        return PageBuilder.distinct(links, n);
    }

    private static int here(long link) {
        return (int) ((link >>> 40) & 0x3F_FFFF);
    }

    private static int other(long link) {
        return (int) ((link >>> 16) & 0xFF_FFFF);
    }

    private static int there(long link) {
        return (int) (link & 0xFFFF);
    }

    /** Joins two sets; the lower node becomes the root, so a component's label is its lowest node. */
    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra < rb) {
            parent[rb] = ra;
        } else if (rb < ra) {
            parent[ra] = rb;
        }
    }

    private static int find(int[] parent, int g) {
        while (parent[g] != g) {
            parent[g] = parent[parent[g]];
            g = parent[g];
        }
        return g;
    }
}
