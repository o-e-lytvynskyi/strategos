package dev.strategos.core.content;

import dev.strategos.core.shared.CoreApi;
import java.util.ArrayList;
import java.util.List;

/**
 * A line of walls (BLD-13, M9 spec §2.10), pure: the run from one cell towards another along its dominant axis (the
 * other coordinate stays the start's: a diagonal is not a staircase, the owner's wall runs straight), cut into
 * segments of {@code length} blocks from the start, the last one reaching past the end when the run is not a multiple.
 * A segment is a blueprint turned along the run (rotation 0 along X, 1 along Z), so segments touch end to end with no
 * gap and no overlap whatever the direction; each stands at its own base, the floor under its middle, and a step
 * between neighbours simply puts the next at its own height (a spread over a segment's own columns is the placement's
 * to refuse, leaving a gap). A segment's index counts from the start; the indices in {@code gates} are gates.
 */
@CoreApi
public final class WallLine {
    /** The value path of a wall template's segment length, in blocks; present on every template that is a wall. */
    public static final String SEGMENT = "Wall/segment";

    /** What a gate template's id adds to its wall template's. */
    public static final String GATE_SUFFIX = "_gate";

    private WallLine() {}

    /** The floor under a column, as {@link PlacementRules.World#floor}. */
    @FunctionalInterface
    public interface Floor {
        double at(int x, int z);
    }

    /**
     * One segment: its index along the line, the match-local corner of its footprint, its rotation (0 along X, 1 along
     * Z), the block its lowest layer stands at ({@code Integer.MIN_VALUE} without land under its middle) and whether
     * it is a gate.
     */
    @CoreApi
    public record Segment(int index, int x, int z, int rotation, int baseY, boolean gate) {}

    /** How many segments the run from (x1, z1) to (x2, z2) needs for {@code length}-block segments, at least one. */
    public static int count(int x1, int z1, int x2, int z2, int length) {
        int run = Math.max(Math.abs(x2 - x1), Math.abs(z2 - z1)) + 1;
        return (run + length - 1) / length;
    }

    /** The segments of the run, in order; none for a length below 1. */
    public static List<Segment> of(int x1, int z1, int x2, int z2, int length, List<Integer> gates, Floor floor) {
        List<Segment> out = new ArrayList<>();
        if (length < 1) {
            return out;
        }
        boolean alongX = Math.abs(x2 - x1) >= Math.abs(z2 - z1);
        int step = (alongX ? x2 >= x1 : z2 >= z1) ? 1 : -1;
        int n = count(x1, z1, x2, z2, length);
        for (int k = 0; k < n; k++) {
            int from = (alongX ? x1 : z1) + step * k * length; // the segment's first column along the run
            int low = step > 0 ? from : from - length + 1; // its lowest column: the footprint's corner
            int mid = low + (length - 1) / 2;
            int x = alongX ? low : x1;
            int z = alongX ? z1 : low;
            double f = alongX ? floor.at(mid, z1) : floor.at(x1, mid);
            int baseY = Double.isNaN(f) ? Integer.MIN_VALUE : (int) Math.round(f) - 1;
            out.add(new Segment(k, x, z, alongX ? 0 : 1, baseY, gates.contains(k)));
        }
        return out;
    }
}
