package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;
import java.util.Arrays;

/**
 * The slot table of an {@link EntityStore}, for snapshots: the generation of every index up to the high-water mark, and
 * the free indices in ascending order. Not a record, because it holds arrays: defensive copies and value equality.
 */
@CoreApi
public final class EntitySlots {
    private final int[] generations;
    private final int[] freeIndices;

    public EntitySlots(int[] generations, int[] freeIndices) {
        this.generations = generations.clone();
        this.freeIndices = freeIndices.clone();
        for (int i = 1; i < this.freeIndices.length; i++) {
            if (this.freeIndices[i] <= this.freeIndices[i - 1]) {
                throw new IllegalArgumentException("free indices must ascend: " + Arrays.toString(freeIndices));
            }
        }
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
