package dev.strategos.core.client.select;

import dev.strategos.core.content.Footprint;
import java.util.ArrayList;
import java.util.List;

/**
 * The selection marker of a resource node (ART-02, M4 spec §2.15): a band {@value #WIDTH} of a block wide round its
 * footprint, one piece on each cell of the ring outside it, so each piece lies on its own cell's ground and the band
 * follows a slope. Solid quads, no particles, so a frozen frame draws it the same every time, as the rally marker.
 */
public final class NodeRing {
    /** How wide the band is, in blocks. */
    public static final double WIDTH = 0.25;

    private NodeRing() {}

    /**
     * One piece of the band: from ({@code x0}, {@code z0}) to ({@code x1}, {@code z1}), match-local, on the ground of
     * cell ({@code cellX}, {@code cellZ}).
     */
    public record Piece(double x0, double z0, double x1, double z1, int cellX, int cellZ) {}

    /** The band round {@code at}: its edges' pieces, then its corners', west to east and north to south. */
    public static List<Piece> of(Footprint at) {
        int ax = at.anchorX();
        int az = at.anchorZ();
        int ex = ax + at.spanX();
        int ez = az + at.spanZ();
        List<Piece> out = new ArrayList<>();
        for (int x = ax; x < ex; x++) {
            out.add(new Piece(x, az - WIDTH, x + 1, az, x, az - 1));
            out.add(new Piece(x, ez, x + 1, ez + WIDTH, x, ez));
        }
        for (int z = az; z < ez; z++) {
            out.add(new Piece(ax - WIDTH, z, ax, z + 1, ax - 1, z));
            out.add(new Piece(ex, z, ex + WIDTH, z + 1, ex, z));
        }
        out.add(new Piece(ax - WIDTH, az - WIDTH, ax, az, ax - 1, az - 1));
        out.add(new Piece(ex, az - WIDTH, ex + WIDTH, az, ex, az - 1));
        out.add(new Piece(ax - WIDTH, ez, ax, ez + WIDTH, ax - 1, ez));
        out.add(new Piece(ex, ez, ex + WIDTH, ez + WIDTH, ex, ez));
        return out;
    }
}
