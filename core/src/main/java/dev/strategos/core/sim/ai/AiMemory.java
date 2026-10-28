package dev.strategos.core.sim.ai;

import java.util.Arrays;
import java.util.List;

/**
 * A computer player's own memory between thinks: {@value #SIZE} numbers its modules agree on (the build step, the next
 * site, the wave). The match saves and hashes it with the player, so a restored match thinks on as the live one.
 */
public final class AiMemory {
    public static final int SIZE = 17;

    private final long[] values = new long[SIZE];

    public long get(int k) {
        return values[k];
    }

    public void set(int k, long v) {
        values[k] = v;
    }

    public List<Long> save() {
        return Arrays.stream(values).boxed().toList();
    }

    public void restore(List<Long> saved) {
        for (int k = 0; k < SIZE && k < saved.size(); k++) {
            values[k] = saved.get(k);
        }
    }

    public long[] copy() {
        return values.clone();
    }
}
