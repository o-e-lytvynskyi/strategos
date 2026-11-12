package dev.strategos.core.client.fog;

import dev.strategos.core.shared.HotPath;
import java.util.Arrays;

/**
 * FOW-07 shroud extraction (04 §13.6): from the client's visible and explored bitsets, per {@value #PAGE}×{@value #PAGE}
 * page of vision cells, quads on the match plane with soft edges from per-corner fog alpha — unexplored black
 * ({@value #UNEXPLORED}), explored dimmed ({@value #EXPLORED}), visible clear ({@value #VISIBLE}). A page's mesh is
 * rebuilt only when its fingerprint changes. Pure math for JMH ({@code ShroudBench}); the mod samples terrain heights
 * and draws.
 */
@HotPath
public final class ShroudExtract {
    /** Vision cells on one side of a rebuild page. */
    public static final int PAGE = 16;

    /** Blocks on one side of a vision cell (same as {@code DiscOffsets.CELL} on the server; duplicated so client never imports sim). */
    public static final int CELL_BLOCKS = 4;

    /** Fog alpha of an unexplored cell. */
    public static final float UNEXPLORED = 1.0F;

    /** Fog alpha of an explored cell that is not visible. */
    public static final float EXPLORED = 0.5F;

    /** Fog alpha of a visible cell (clear). */
    public static final float VISIBLE = 0.0F;

    private ShroudExtract() {}

    /**
     * Reusable page mesh: match-local XZ of each cell corner and the fog alpha there, four corners per quad, as many
     * as {@link #quads()}. Arrays grow and are reused, never shrunk.
     */
    public static final class PageMesh {
        private float[] data = new float[16 * 64]; // 4 corners × (x, z, a) = 12 floats per quad
        private int quads;
        private long fingerprint;
        private boolean held; // true once extractPage has filled this mesh at least once

        public int quads() {
            return quads;
        }

        public long fingerprint() {
            return fingerprint;
        }

        /** Whether this mesh has been extracted at least once (empty pages stay held with zero quads). */
        public boolean held() {
            return held;
        }

        /** Corner {@code c} (0..3: SW, SE, NE, NW) of quad {@code q}: match-local X. */
        public float x(int q, int c) {
            return data[12 * q + 3 * c];
        }

        public float z(int q, int c) {
            return data[12 * q + 3 * c + 1];
        }

        public float alpha(int q, int c) {
            return data[12 * q + 3 * c + 2];
        }

        void clear() {
            quads = 0;
        }

        void setFingerprint(long fingerprint) {
            this.fingerprint = fingerprint;
            this.held = true;
        }

        /** Drops the held fingerprint so the next extract rebuilds (match left or vision cleared). */
        public void invalidate() {
            held = false;
            quads = 0;
            fingerprint = 0;
        }

        void add(
                float x0,
                float z0,
                float a0,
                float x1,
                float z1,
                float a1,
                float x2,
                float z2,
                float a2,
                float x3,
                float z3,
                float a3) {
            if (12 * (quads + 1) > data.length) {
                data = Arrays.copyOf(data, data.length * 2);
            }
            int at = 12 * quads++;
            data[at] = x0;
            data[at + 1] = z0;
            data[at + 2] = a0;
            data[at + 3] = x1;
            data[at + 4] = z1;
            data[at + 5] = a1;
            data[at + 6] = x2;
            data[at + 7] = z2;
            data[at + 8] = a2;
            data[at + 9] = x3;
            data[at + 10] = z3;
            data[at + 11] = a3;
        }
    }

    /** How many pages cover {@code cells} cells on one axis. */
    public static int pages(int cells) {
        return cells <= 0 ? 0 : (cells + PAGE - 1) / PAGE;
    }

    /**
     * A stable fingerprint of the fog state of page ({@code pageX}, {@code pageZ}). Equal fingerprints mean the page's
     * cells are unchanged; the renderer rebuilds only when this differs from the mesh it holds.
     */
    public static long fingerprint(long[] visible, long[] explored, int cellsX, int cellsZ, int pageX, int pageZ) {
        int x0 = pageX * PAGE;
        int z0 = pageZ * PAGE;
        int x1 = Math.min(x0 + PAGE, cellsX);
        int z1 = Math.min(z0 + PAGE, cellsZ);
        long h = 0x9E3779B97F4A7C15L ^ ((long) cellsX << 32) ^ cellsZ;
        for (int z = z0; z < z1; z++) {
            for (int x = x0; x < x1; x++) {
                int i = z * cellsX + x;
                long bit = 1L << (i & 63);
                int word = i >> 6;
                int v = (visible[word] & bit) != 0 ? 2 : 0;
                int e = (explored[word] & bit) != 0 ? 1 : 0;
                h = Long.rotateLeft(h, 5) ^ (v + e) ^ ((long) x * 0x85EBCA77L) ^ ((long) z * 0xC2B2AE3DL);
            }
        }
        return h;
    }

    /**
     * Fills {@code out} with one quad per cell of page ({@code pageX}, {@code pageZ}) that is not fully clear after
     * soft-edge averaging. Corner alphas average the fog of the cells that meet at that corner. Match-local block
     * coordinates use {@link #CELL_BLOCKS} per cell. Skips the rebuild when {@code out}'s fingerprint already matches.
     *
     * @return {@code true} when the mesh was rebuilt
     */
    public static boolean extractPage(
            long[] visible, long[] explored, int cellsX, int cellsZ, int pageX, int pageZ, PageMesh out) {
        long fp = fingerprint(visible, explored, cellsX, cellsZ, pageX, pageZ);
        if (out.held() && out.fingerprint() == fp) {
            return false;
        }
        out.clear();
        out.setFingerprint(fp);
        int x0 = pageX * PAGE;
        int z0 = pageZ * PAGE;
        int x1 = Math.min(x0 + PAGE, cellsX);
        int z1 = Math.min(z0 + PAGE, cellsZ);
        for (int cz = z0; cz < z1; cz++) {
            for (int cx = x0; cx < x1; cx++) {
                float a00 = cornerAlpha(visible, explored, cellsX, cellsZ, cx, cz);
                float a10 = cornerAlpha(visible, explored, cellsX, cellsZ, cx + 1, cz);
                float a11 = cornerAlpha(visible, explored, cellsX, cellsZ, cx + 1, cz + 1);
                float a01 = cornerAlpha(visible, explored, cellsX, cellsZ, cx, cz + 1);
                if (a00 <= 0 && a10 <= 0 && a11 <= 0 && a01 <= 0) {
                    continue;
                }
                float bx0 = cx * (float) CELL_BLOCKS;
                float bz0 = cz * (float) CELL_BLOCKS;
                float bx1 = (cx + 1) * (float) CELL_BLOCKS;
                float bz1 = (cz + 1) * (float) CELL_BLOCKS;
                // SW, SE, NE, NW — winding for an upward normal
                out.add(bx0, bz0, a00, bx1, bz0, a10, bx1, bz1, a11, bx0, bz1, a01);
            }
        }
        return true;
    }

    /** Extracts every page of a {@code cellsX}×{@code cellsZ} grid into {@code pages} (row-major by pageZ, pageX). */
    public static int extractAll(long[] visible, long[] explored, int cellsX, int cellsZ, PageMesh[] pages) {
        int px = pages(cellsX);
        int pz = pages(cellsZ);
        int rebuilt = 0;
        int i = 0;
        for (int z = 0; z < pz; z++) {
            for (int x = 0; x < px; x++) {
                if (extractPage(visible, explored, cellsX, cellsZ, x, z, pages[i++])) {
                    rebuilt++;
                }
            }
        }
        return rebuilt;
    }

    /** Fog alpha of one cell: visible clear, else explored dimmed, else unexplored black. Out of bounds is unexplored. */
    public static float cellAlpha(long[] visible, long[] explored, int cellsX, int cellsZ, int cellX, int cellZ) {
        if (cellX < 0 || cellX >= cellsX || cellZ < 0 || cellZ >= cellsZ) {
            return UNEXPLORED;
        }
        int i = cellZ * cellsX + cellX;
        long bit = 1L << (i & 63);
        int word = i >> 6;
        if ((visible[word] & bit) != 0) {
            return VISIBLE;
        }
        if ((explored[word] & bit) != 0) {
            return EXPLORED;
        }
        return UNEXPLORED;
    }

    /**
     * Soft-edge alpha at grid corner ({@code cornerX}, {@code cornerZ}): the mean of the (up to) four cells that meet
     * there. Corners run from 0 to {@code cellsX} / {@code cellsZ} inclusive.
     */
    public static float cornerAlpha(long[] visible, long[] explored, int cellsX, int cellsZ, int cornerX, int cornerZ) {
        float sum = 0;
        int n = 0;
        for (int dz = -1; dz <= 0; dz++) {
            for (int dx = -1; dx <= 0; dx++) {
                int cx = cornerX + dx;
                int cz = cornerZ + dz;
                if (cx >= 0 && cx < cellsX && cz >= 0 && cz < cellsZ) {
                    sum += cellAlpha(visible, explored, cellsX, cellsZ, cx, cz);
                    n++;
                }
            }
        }
        return n == 0 ? UNEXPLORED : sum / n;
    }

    /** Sets bit {@code index} in {@code bits}. */
    public static void setBit(long[] bits, int index) {
        bits[index >> 6] |= 1L << (index & 63);
    }

    /** Word count for a {@code cellsX}×{@code cellsZ} bitset. */
    public static int words(int cellsX, int cellsZ) {
        int cells = cellsX * cellsZ;
        return cells <= 0 ? 0 : (cells + 63) / 64;
    }
}
