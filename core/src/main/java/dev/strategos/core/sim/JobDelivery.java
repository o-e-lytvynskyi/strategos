package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;
import java.util.Arrays;

/**
 * The path requests whose jobs an {@code ADAPTIVE} adapter found finished before this tick (PATH-16, ADR-0015): this
 * tick's delivery applies the results of those that are due. It is journaled, so a replay delivers exactly as the live
 * run did, whatever threads it has. The ids are kept ascending, each once.
 */
@CoreApi
public final class JobDelivery implements InputEvent {
    /** The most ids one delivery may carry. */
    public static final int MAX_IDS = 1 << 20;

    private final int[] ids;

    /** A delivery of {@code requestIds}, sorted, each once; ids are positive. */
    public JobDelivery(int... requestIds) {
        if (requestIds.length > MAX_IDS) {
            throw new IllegalArgumentException(requestIds.length + " ids in one delivery, more than " + MAX_IDS);
        }
        int[] sorted = requestIds.clone();
        Arrays.sort(sorted);
        int n = 0;
        for (int id : sorted) {
            if (id < 1) {
                throw new IllegalArgumentException("request ids start at 1, not " + id);
            }
            if (n == 0 || sorted[n - 1] != id) {
                sorted[n++] = id;
            }
        }
        this.ids = Arrays.copyOf(sorted, n);
    }

    public int count() {
        return ids.length;
    }

    /** The {@code k}th id, ascending. */
    public int id(int k) {
        return ids[k];
    }

    public boolean contains(int id) {
        return Arrays.binarySearch(ids, id) >= 0;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof JobDelivery other && Arrays.equals(ids, other.ids);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(ids);
    }

    @Override
    public String toString() {
        return "JobDelivery" + Arrays.toString(ids);
    }
}
