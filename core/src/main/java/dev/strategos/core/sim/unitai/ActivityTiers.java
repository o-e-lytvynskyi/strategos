package dev.strategos.core.sim.unitai;

import java.util.Arrays;

/**
 * When each unit was last ordered (UNIT-05), which makes it ACTIVE for {@value ActivityTier#ORDERED} ticks: the view's
 * statistic, as the stuck ladder's rungs are, neither hashed nor saved, so a restored match counts its tiers afresh.
 */
public final class ActivityTiers {
    private long[] orderedAt = new long[0];

    /** Unit index {@code i} took an order at {@code tick}. */
    public void ordered(int i, long tick) {
        if (i >= orderedAt.length) {
            int old = orderedAt.length;
            orderedAt = Arrays.copyOf(orderedAt, Math.max(i + 1, old * 2));
            Arrays.fill(orderedAt, old, orderedAt.length, Long.MIN_VALUE / 2);
        }
        orderedAt[i] = tick;
    }

    /** Whether unit index {@code i} was ordered within {@value ActivityTier#ORDERED} ticks of {@code tick}. */
    public boolean recent(int i, long tick) {
        return i < orderedAt.length && tick - orderedAt[i] < ActivityTier.ORDERED;
    }
}
