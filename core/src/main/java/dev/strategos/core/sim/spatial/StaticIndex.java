package dev.strategos.core.sim.spatial;

import java.util.Arrays;

/**
 * Entities that do not move, by 16 × 16 page (04 §10.1): buildings, resource nodes and dropsites, each page keeping
 * its entities' indices sorted, updated as they are added and removed. The nearest node of a resource and the nearest
 * dropsite of a faction for a resource are searched page ring by page ring, the lower index winning a tie; nothing
 * allocates. Empty in M1 outside its tests: the economy and building cards fill it.
 */
public final class StaticIndex {
    public static final int BUILDING = 1;
    public static final int NODE = 2;
    public static final int DROPSITE = 4;

    private static final int PAGE = 16;

    private final int pagesX;
    private final int pagesZ;
    private final int[][] pages; // per page, its entities' indices, sorted
    private final int[] pageCounts;
    private int[] pageOf = new int[0]; // per entity index, its page, or -1 when absent
    private int[] kinds = new int[0];
    private double[] xs = new double[0];
    private double[] zs = new double[0];
    private int[] factions = new int[0];
    private long[] resources = new long[0];
    private int size;

    public StaticIndex(int sizeX, int sizeZ) {
        this.pagesX = Math.max(1, (sizeX + PAGE - 1) / PAGE);
        this.pagesZ = Math.max(1, (sizeZ + PAGE - 1) / PAGE);
        this.pages = new int[pagesX * pagesZ][];
        for (int p = 0; p < pages.length; p++) {
            pages[p] = new int[4];
        }
        this.pageCounts = new int[pagesX * pagesZ];
    }

    /**
     * Entity {@code index} of {@code kinds} (bits of {@link #BUILDING}, {@link #NODE}, {@link #DROPSITE}) at (x, z), of
     * {@code faction} (-1 for none), holding or taking the resources whose bits {@code resources} sets; an entity
     * already in the index moves.
     */
    public void add(int index, int kinds, double x, double z, int faction, long resources) {
        if (index >= pageOf.length) {
            grow(index + 1);
        }
        if (pageOf[index] >= 0) {
            remove(index);
        }
        int p = page(x, z);
        int n = pageCounts[p];
        if (n == pages[p].length) {
            pages[p] = Arrays.copyOf(pages[p], n * 2);
        }
        int at = -Arrays.binarySearch(pages[p], 0, n, index) - 1;
        System.arraycopy(pages[p], at, pages[p], at + 1, n - at);
        pages[p][at] = index;
        pageCounts[p] = n + 1;
        pageOf[index] = p;
        this.kinds[index] = kinds;
        xs[index] = x;
        zs[index] = z;
        factions[index] = faction;
        this.resources[index] = resources;
        size++;
    }

    public void remove(int index) {
        if (index >= pageOf.length || pageOf[index] < 0) {
            return;
        }
        int p = pageOf[index];
        int n = pageCounts[p];
        int at = Arrays.binarySearch(pages[p], 0, n, index);
        System.arraycopy(pages[p], at + 1, pages[p], at, n - at - 1);
        pageCounts[p] = n - 1;
        pageOf[index] = -1;
        size--;
    }

    public int size() {
        return size;
    }

    /** The node nearest (x, z) holding resource {@code resource}, or -1. */
    public int nearestNode(int resource, double x, double z) {
        return nearest(x, z, NODE, -1, 1L << resource);
    }

    /** The dropsite of {@code faction} nearest (x, z) that takes resource {@code resource}, or -1. */
    public int nearestDropsite(int faction, int resource, double x, double z) {
        return nearest(x, z, DROPSITE, faction, 1L << resource);
    }

    private int nearest(double x, double z, int kind, int faction, long resource) {
        int qx = clamp((int) Math.floor(x / PAGE), pagesX);
        int qz = clamp((int) Math.floor(z / PAGE), pagesZ);
        int best = -1;
        double bestD2 = Double.POSITIVE_INFINITY;
        int rings = Math.max(pagesX, pagesZ);
        for (int ring = 0; ring <= rings; ring++) {
            if (best >= 0 && ring > 0) {
                double bound = outside(x, z, qx, qz, ring - 1);
                if (bound * bound > bestD2) {
                    break;
                }
            }
            for (int pz = qz - ring; pz <= qz + ring; pz++) {
                if (pz < 0 || pz >= pagesZ) {
                    continue;
                }
                int step = ring == 0 || pz == qz - ring || pz == qz + ring ? 1 : 2 * ring;
                for (int px = qx - ring; px <= qx + ring; px += step) {
                    if (px < 0 || px >= pagesX) {
                        continue;
                    }
                    int p = pz * pagesX + px;
                    for (int k = 0; k < pageCounts[p]; k++) {
                        int e = pages[p][k];
                        if ((kinds[e] & kind) == 0
                                || (resources[e] & resource) == 0
                                || (faction >= 0 && factions[e] != faction)) {
                            continue;
                        }
                        double dx = xs[e] - x;
                        double dz = zs[e] - z;
                        double d2 = dx * dx + dz * dz;
                        if (d2 < bestD2 || (d2 == bestD2 && e < best)) {
                            best = e;
                            bestD2 = d2;
                        }
                    }
                }
            }
        }
        return best;
    }

    private static double outside(double x, double z, int qx, int qz, int within) {
        double x0 = (double) (qx - within) * PAGE;
        double x1 = (double) (qx + within + 1) * PAGE;
        double z0 = (double) (qz - within) * PAGE;
        double z1 = (double) (qz + within + 1) * PAGE;
        return Math.max(0, Math.min(Math.min(x - x0, x1 - x), Math.min(z - z0, z1 - z)));
    }

    private int page(double x, double z) {
        return clamp((int) Math.floor(z / PAGE), pagesZ) * pagesX + clamp((int) Math.floor(x / PAGE), pagesX);
    }

    private static int clamp(int v, int limit) {
        return Math.max(0, Math.min(limit - 1, v));
    }

    private void grow(int needed) {
        int capacity = Math.max(needed, pageOf.length * 2);
        int old = pageOf.length;
        pageOf = Arrays.copyOf(pageOf, capacity);
        Arrays.fill(pageOf, old, capacity, -1);
        kinds = Arrays.copyOf(kinds, capacity);
        xs = Arrays.copyOf(xs, capacity);
        zs = Arrays.copyOf(zs, capacity);
        factions = Arrays.copyOf(factions, capacity);
        resources = Arrays.copyOf(resources, capacity);
    }
}
