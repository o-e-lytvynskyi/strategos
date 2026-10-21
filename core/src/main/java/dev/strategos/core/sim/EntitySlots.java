package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.Handle;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * The slot table of an {@link EntityStore}, for snapshots: the generation of every index up to the high-water mark, and
 * the free indices in ascending order. Not a record, because it holds arrays: defensive copies and value equality.
 */
@CoreApi
public final class EntitySlots {
    private final int[] generations;
    private final int[] freeIndices;

    public EntitySlots(int[] generations, int[] freeIndices) {
        String problem = problem(generations, freeIndices);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        this.generations = generations.clone();
        this.freeIndices = freeIndices.clone();
    }

    /**
     * What makes the arrays no slot table: more indices than a handle can name, a generation outside
     * 0..{@link Handle#MAX_GENERATION}, or free indices that do not ascend inside the table. Null when they are one. A
     * decoder reports it as an error, never by throwing.
     */
    public static @Nullable String problem(int[] generations, int[] freeIndices) {
        if (generations.length > Handle.MAX_INDEX + 1) {
            return generations.length + " slots, more than the " + (Handle.MAX_INDEX + 1) + " a handle can name";
        }
        for (int i = 0; i < generations.length; i++) {
            if (generations[i] < 0 || generations[i] > Handle.MAX_GENERATION) {
                return "slot " + i + " has generation " + generations[i] + ", outside 0.." + Handle.MAX_GENERATION;
            }
        }
        for (int i = 0; i < freeIndices.length; i++) {
            if (freeIndices[i] < 0 || freeIndices[i] >= generations.length) {
                return "free index " + freeIndices[i] + " outside 0.." + (generations.length - 1);
            }
            if (i > 0 && freeIndices[i] <= freeIndices[i - 1]) {
                return "free indices must ascend: " + freeIndices[i - 1] + " then " + freeIndices[i];
            }
        }
        return null;
    }

    public int[] generations() {
        return generations.clone();
    }

    public int[] freeIndices() {
        return freeIndices.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EntitySlots other
                && Arrays.equals(generations, other.generations)
                && Arrays.equals(freeIndices, other.freeIndices);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(generations) + Arrays.hashCode(freeIndices);
    }

    @Override
    public String toString() {
        return "EntitySlots[generations=" + Arrays.toString(generations) + ", free=" + Arrays.toString(freeIndices)
                + "]";
    }
}
