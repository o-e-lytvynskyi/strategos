package dev.strategos.core.testkit;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.Rng;
import dev.strategos.core.shared.TemplateId;
import java.util.Objects;

/**
 * Who stands where in a roster duel (UNIT-07, M5 spec 2.7): two groups of equal cost, their sizes, the gap between
 * their fronts and each body's seeded place. Shared by the L1 duels ({@code Duel}) and the L3 smoke, so both measure
 * the same fight. Every draw is {@link Rng} on {@link #STREAM}, a pure function of the seed.
 */
public final class DuelLayout {
    /** The test kit's stream for duel placement (RandomCommands has 1001). */
    static final int STREAM = 1002;

    /** The least total cost a side fields; each group is the smallest whole-unit count at or over it that costs the same. */
    static final int MIN_BUDGET = 600;

    static final double OPEN_GAP = 12;
    static final double RANGE_GAP = 18;
    /** Between the fronts of a hill duel: inside the hill's reach of 26, outside the flat ground's 20. */
    static final double HILL_GAP = 23;
    /** The hill of the CMB-16 duel, in blocks. */
    public static final double HILL = 6;

    private static final TemplateSet SET = TemplateSet.builtin();
    private static final double COLUMN = 2.0; // across the front, wider than a horseman
    private static final double ROW = 2.0; // depth between ranks
    private static final int WIDTH = 3; // bodies across the front
    private static final double JITTER = 0.4;

    /** A roster unit. Its cost is read from the template, so a tuned cost resizes the groups. */
    public enum Kind {
        SPEARMAN("strategos:unit/infantry"),
        ARCHER("strategos:unit/archer"),
        HORSEMAN("strategos:unit/horseman");

        private final String id;

        Kind(String id) {
            this.id = id;
        }

        public TemplateId id() {
            return new TemplateId(id);
        }

        /** Food, wood and iron together, whole units. */
        public int cost() {
            int index = Objects.requireNonNull(SET.byId(id())).netIndex();
            double sum = 0;
            for (String resource : new String[] {"food", "wood", "iron"}) {
                var path = SET.valuePaths().byPath("Cost/resources/" + resource);
                double v = path == null ? Double.NaN : SET.value(index, path.id());
                sum += Double.isNaN(v) ? 0 : v;
            }
            return (int) Math.round(sum);
        }
    }

    /** A body's place, match-local. */
    public record Spot(double x, double z) {}

    private DuelLayout() {}

    /** The group sizes of {@code first} against {@code second}: equal total cost. */
    public static int[] counts(Kind first, Kind second) {
        int a = first.cost();
        int b = second.cost();
        long lcm = (long) a / gcd(a, b) * b;
        long total = lcm * ((MIN_BUDGET + lcm - 1) / lcm);
        return new int[] {(int) (total / a), (int) (total / b)};
    }

    /** The gap between the fronts: an archer shooting at spearmen starts at range, any other pair in the open. */
    public static double gap(Kind first, Kind second) {
        boolean archers = first == Kind.ARCHER || second == Kind.ARCHER;
        boolean spears = first == Kind.SPEARMAN || second == Kind.SPEARMAN;
        return archers && spears ? RANGE_GAP : OPEN_GAP;
    }

    /** Whether the first group stands west of the second on {@code seed}. */
    public static boolean firstWest(long seed) {
        return Rng.below(seed, STREAM, 0, 0, 0, 2) == 0;
    }

    /** Which of the {@code enemies} bodies group {@code group} opens on, an index into the enemy group. */
    public static int target(long seed, int group, int enemies) {
        return Rng.below(seed, STREAM, 0, group, 1, enemies);
    }

    /**
     * Body {@code k} of group {@code group} ({@code count} bodies), in ranks of three, its front rank {@code gap / 2}
     * from the centre line on side {@code side} (-1 west, 1 east), jittered.
     */
    public static Spot place(
            long seed, int group, int k, int count, int side, double gap, double centerX, double centerZ) {
        int rank = k / WIDTH;
        int column = k % WIDTH;
        int inRank = Math.min(WIDTH, count - rank * WIDTH);
        double x = centerX + side * (gap / 2 + rank * ROW) + jitter(seed, group, k, 0);
        double z = centerZ + (column - (inRank - 1) / 2.0) * COLUMN + jitter(seed, group, k, 1);
        return new Spot(x, z);
    }

    private static double jitter(long seed, int group, int k, int axis) {
        return (Rng.unit(seed, STREAM, 0, 2 + group * 64L + k, axis) * 2 - 1) * JITTER;
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }
}
