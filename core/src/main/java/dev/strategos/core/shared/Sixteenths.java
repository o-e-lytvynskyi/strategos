package dev.strategos.core.shared;

/** Heights in 1/16 block, the unit the nav grid stores floors and clearances in. */
public final class Sixteenths {
    public static final int PER_BLOCK = 16;

    private Sixteenths() {}

    public static double toBlocks(int sixteenths) {
        return sixteenths / (double) PER_BLOCK;
    }

    /** The largest whole number of sixteenths at or below {@code blocks}. */
    public static int floorOf(double blocks) {
        return (int) StrictMath.floor(blocks * PER_BLOCK);
    }
}
