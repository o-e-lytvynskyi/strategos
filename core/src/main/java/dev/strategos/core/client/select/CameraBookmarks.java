package dev.strategos.core.client.select;

import org.jspecify.annotations.Nullable;

/**
 * The camera's four bookmarks (UI-17): a place each, set from where the camera looks and jumped back to. The session's
 * own, not saved; a new match starts with none.
 */
public final class CameraBookmarks {
    /** How many bookmarks there are. */
    public static final int COUNT = 4;

    private final double[][] places = new double[COUNT][];

    /** Keeps ({@code x}, {@code z}) as bookmark {@code slot}, 0 to 3, over any it held. */
    public void set(int slot, double x, double z) {
        places[check(slot)] = new double[] {x, z};
    }

    /** Bookmark {@code slot}'s place as x and z, or null when it was never set. */
    public double @Nullable [] get(int slot) {
        double[] p = places[check(slot)];
        return p == null ? null : p.clone();
    }

    /** Forgets every bookmark. */
    public void clear() {
        java.util.Arrays.fill(places, null);
    }

    private static int check(int slot) {
        if (slot < 0 || slot >= COUNT) {
            throw new IllegalArgumentException("no bookmark " + slot);
        }
        return slot;
    }
}
