package dev.strategos.core.sim.territory;

import dev.strategos.core.shared.CoreApi;
import java.util.Arrays;

/**
 * The influence sources of one recompute (M9 spec §2.2), as parallel arrays: a frozen copy the kernel reads, on the
 * tick thread or in a job. Centres are in half-blocks (twice the block coordinate), so a footprint's centre between
 * two blocks is exact; radii are in blocks.
 */
@CoreApi
public final class TerritorySources {
    private int size;
    private int[] faction = new int[16];
    private int[] centreX2 = new int[16];
    private int[] centreZ2 = new int[16];
    private int[] weight = new int[16];
    private int[] radius = new int[16];
    private boolean[] root = new boolean[16];

    /** Adds a source; a weight or radius of zero or less adds nothing. */
    public void add(int faction, int centreX2, int centreZ2, int weight, int radius, boolean root) {
        if (weight <= 0 || radius <= 0) {
            return;
        }
        if (size == this.faction.length) {
            int n = size * 2;
            this.faction = Arrays.copyOf(this.faction, n);
            this.centreX2 = Arrays.copyOf(this.centreX2, n);
            this.centreZ2 = Arrays.copyOf(this.centreZ2, n);
            this.weight = Arrays.copyOf(this.weight, n);
            this.radius = Arrays.copyOf(this.radius, n);
            this.root = Arrays.copyOf(this.root, n);
        }
        this.faction[size] = faction;
        this.centreX2[size] = centreX2;
        this.centreZ2[size] = centreZ2;
        this.weight[size] = weight;
        this.radius[size] = radius;
        this.root[size] = root;
        size++;
    }

    public void clear() {
        size = 0;
    }

    public int size() {
        return size;
    }

    public int faction(int i) {
        return faction[i];
    }

    public int centreX2(int i) {
        return centreX2[i];
    }

    public int centreZ2(int i) {
        return centreZ2[i];
    }

    public int weight(int i) {
        return weight[i];
    }

    public int radius(int i) {
        return radius[i];
    }

    public boolean root(int i) {
        return root[i];
    }

    /** An independent copy, for a job that must not see later changes. */
    public TerritorySources copy() {
        TerritorySources c = new TerritorySources();
        c.size = size;
        c.faction = Arrays.copyOf(faction, Math.max(size, 1));
        c.centreX2 = Arrays.copyOf(centreX2, Math.max(size, 1));
        c.centreZ2 = Arrays.copyOf(centreZ2, Math.max(size, 1));
        c.weight = Arrays.copyOf(weight, Math.max(size, 1));
        c.radius = Arrays.copyOf(radius, Math.max(size, 1));
        c.root = Arrays.copyOf(root, Math.max(size, 1));
        return c;
    }

    /**
     * The influence of a source of {@code weight} and {@code radius} blocks at a squared distance of {@code d2}
     * half-blocks²: linear falloff, {@code weight × (r − d) / r} for d < r, else 0; in integers, so every machine
     * agrees.
     */
    public static long influence(int weight, int radius, long d2) {
        long r = 2L * radius;
        long d = isqrt(d2);
        return d >= r ? 0 : weight * (r - d) / r;
    }

    /** The floor of the square root of {@code v} ≥ 0, exact for every long. */
    public static long isqrt(long v) {
        long r = (long) Math.sqrt((double) v);
        while (r * r > v) {
            r--;
        }
        while ((r + 1) * (r + 1) <= v) {
            r++;
        }
        return r;
    }
}
