package dev.strategos.core.shared;

/**
 * Where the members of a group order stand (ORD-03, spec §5.9): {@code n} places in rows across the facing, the
 * direction from the group's centroid to the target, {@code spacing} apart ({@link #spacing}), {@code width} to a row,
 * the block of rows
 * centred on the target; the last row, if short, is centred too. Pure geometry, so the client previews the same places
 * the server plans (UI-05). Places are numbered row by row from the front, the row farthest along the facing, and
 * across each row from the left, the facing's anticlockwise side.
 */
@CoreApi
public final class SlotLayout {
    /** The least distance between two places, in blocks. */
    public static final double MIN_SPACING = 0.8;

    /** The clearance added between places, in blocks. */
    public static final double CLEARANCE = 0.1;

    private SlotLayout() {}

    /**
     * The distance between the places of a group whose widest body has {@code radius}: room for a body to pass between
     * two standing ones, four radii and the clearance, so a member never finds its place walled in by members that
     * arrived first.
     */
    public static double spacing(double radius) {
        return Math.max(MIN_SPACING, 4 * radius + CLEARANCE);
    }

    /** The default width: the square's side, ⌈√n⌉. */
    public static int width(int n) {
        int w = (int) Math.ceil(Math.sqrt(n));
        while (w * w < n) {
            w++;
        }
        return Math.max(1, w);
    }

    /** The lateral offset of place {@code k}: positive to the facing's left. */
    public static double lateral(int k, int n, int width, double spacing) {
        int row = k / width;
        int inRow = Math.min(width, n - row * width);
        int column = k % width;
        return ((inRow - 1) / 2.0 - column) * spacing;
    }

    /** The depth of place {@code k} along the facing, positive ahead of the target. */
    public static double depth(int k, int n, int width, double spacing) {
        int rows = (n + width - 1) / width;
        int row = k / width;
        return ((rows - 1) / 2.0 - row) * spacing;
    }

    /**
     * Place {@code k}'s X: the target plus its lateral offset along the facing's left, (-fz, fx), and its depth along
     * the facing (fx, fz), a unit vector.
     */
    public static double x(int k, int n, int width, double spacing, double targetX, double fx, double fz) {
        return targetX - fz * lateral(k, n, width, spacing) + fx * depth(k, n, width, spacing);
    }

    public static double z(int k, int n, int width, double spacing, double targetZ, double fx, double fz) {
        return targetZ + fx * lateral(k, n, width, spacing) + fz * depth(k, n, width, spacing);
    }
}
