package dev.strategos.mod.server.match;

/**
 * Where arena matches go (04 §3.2, MAP-07.5): match slot {@code n} starts at x = n × 2,048, z = 0, a chunk corner, so
 * spaces up to 512² stay 1,536 blocks apart. The registry blob counts the slots handed out, so none is ever reused.
 */
public final class SlotAllocator {
    public static final int SPACING = 2_048;

    /** The farthest block coordinate a world border allows (WorldBorder.java:28). */
    public static final int WORLD_EDGE = 29_999_984;

    /** The last slot whose largest space ends inside the world border. */
    public static final int LAST_SLOT = (WORLD_EDGE - MatchRegistry.MAX_SIZE) / SPACING;

    private SlotAllocator() {}

    /** The block X where slot {@code slot}'s space starts; its Z is 0. */
    public static int originX(int slot) {
        if (slot < 0 || slot > LAST_SLOT) {
            throw new MatchRefusedException("arena slot " + slot + " outside 0.." + LAST_SLOT);
        }
        return slot * SPACING;
    }
}
