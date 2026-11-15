package dev.strategos.gametest.suites;

/** The complete QA-12 seed range, or a bounded deterministic CI shard of that same range. */
public record AiSoakSeeds(int first, int count) {
    public static final int TOTAL = 20;

    public AiSoakSeeds {
        if (first < 1 || count < 1 || (long) first + count > TOTAL + 1L) {
            throw new IllegalArgumentException("AI soak range must stay inside seeds 1..20");
        }
    }

    public long seedAt(int index) {
        if (index < 0 || index >= count) throw new IndexOutOfBoundsException(index);
        return first + (long) index;
    }
}
