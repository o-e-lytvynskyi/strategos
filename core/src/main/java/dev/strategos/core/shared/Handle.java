package dev.strategos.core.shared;

/** Dense entity handles: (generation << 20) | index. Generation 0 is never issued, so 0 is NONE. */
public final class Handle {
    public static final int NONE = 0;
    public static final int INDEX_BITS = 20;
    public static final int MAX_INDEX = (1 << INDEX_BITS) - 1; // 1,048,575
    public static final int MAX_GENERATION = (1 << (32 - INDEX_BITS)) - 1; // 4,095

    private Handle() {}

    /** The handle of slot {@code index} at {@code generation}; never {@link #NONE}. */
    public static int of(int index, int generation) {
        if (index < 0 || index > MAX_INDEX) {
            throw new IllegalArgumentException("index " + index + " outside 0.." + MAX_INDEX);
        }
        if (generation < 1 || generation > MAX_GENERATION) {
            throw new IllegalArgumentException("generation " + generation + " outside 1.." + MAX_GENERATION);
        }
        return (generation << INDEX_BITS) | index;
    }

    public static int index(int handle) {
        return handle & MAX_INDEX;
    }

    public static int generation(int handle) {
        return handle >>> INDEX_BITS;
    }

    /** The generation a slot gets on reuse: 1..4095, wrapping 4095 to 1; 0 (a fresh slot) gives 1. */
    public static int nextGeneration(int generation) {
        if (generation < 0 || generation > MAX_GENERATION) {
            throw new IllegalArgumentException("generation " + generation + " outside 0.." + MAX_GENERATION);
        }
        return generation == MAX_GENERATION ? 1 : generation + 1;
    }

    /** {@code "#<index>.<generation>"}. */
    public static String toString(int handle) {
        return "#" + index(handle) + "." + generation(handle);
    }
}
