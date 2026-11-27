package dev.strategos.core.sim.ai;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * What a strategy script says of the late game (AI-09, M10 §2.19); each part is off at its zero, so a script without a
 * {@code depth} block plays as it did: {@code techs} is the priority list of the technology tree, {@code healers} how
 * many healers to keep once a temple stands, {@code charge} the ability the mounted soldiers use closing on the enemy,
 * {@code march} the distance in blocks from which a wave marches in formation (0 for never) and {@code veterans} how
 * ranked soldiers are spared. {@code hold} is how many ticks the stock is kept for a building of the build order after its
 * research step (Town) before soldiers may spend it again; 0 keeps the build order's own minute
 * ({@link BuildManager#RESERVE_TICKS}). {@code techHold} is the same for a technology of the tree (0: five minutes), and {@code phaseHold} for a
 * phase (City), worth a longer wait than the tree's other technologies (0 takes {@code techHold}'s). {@code workers}
 * raises the opening's worker cap by the number named for each technology (a phase) the faction has researched: the
 * deeper game feeds a bigger army.
 */
public record Depth(
        Techs techs,
        int healers,
        Charge charge,
        int march,
        Veterans veterans,
        int hold,
        int techHold,
        int phaseHold,
        Map<String, Integer> workers) {
    /** Every part off. */
    public static final Depth NONE = new Depth(Techs.NONE, 0, Charge.NONE, 0, Veterans.NONE, 0, 0, 0, new TreeMap<>());

    public Depth {
        workers = Collections.unmodifiableMap(new TreeMap<>(workers)); // in key order, whatever the codec gave
    }

    /**
     * Whether the depth path's late game has begun at {@code tick}: a tree is named and its minute ({@code techs.from})
     * has come. Without a {@code depth} block it never does, and nothing that follows it applies (AI-09).
     */
    public boolean late(long tick) {
        return !techs.order().isEmpty() && tick >= 20L * techs.from();
    }

    /** How many more workers the script keeps for the technologies {@code researched} accepts (order is no matter). */
    public int extraWorkers(Predicate<String> researched) {
        int extra = 0;
        for (Map.Entry<String, Integer> e : workers.entrySet()) {
            if (researched.test(e.getKey())) {
                extra += e.getValue();
            }
        }
        return extra;
    }

    /**
     * The tree's priority list: from {@code from} seconds of play, the first technology of {@code order} the rules
     * would take now is researched, once the stock pays for it (the stock is kept for it, as for a build order's step);
     * a tier follows the one it supersedes and one of each exclusive pair is the script's to name.
     */
    public record Techs(int from, List<String> order) {
        public static final Techs NONE = new Techs(0, List.of());

        static final Codec<Techs> CODEC = RecordCodecBuilder.create(i -> i.group(
                        Codec.intRange(0, 100_000).optionalFieldOf("from", 0).forGetter(Techs::from),
                        Codec.STRING
                                .listOf()
                                .optionalFieldOf("order", List.of())
                                .forGetter(Techs::order))
                .apply(i, Techs::new));

        public Techs {
            order = List.copyOf(order);
        }
    }

    /** The ability a mounted soldier uses once an enemy soldier is within {@code range} blocks. */
    public record Charge(String ability, int range) {
        public static final Charge NONE = new Charge("", 0);

        static final Codec<Charge> CODEC = RecordCodecBuilder.create(i -> i.group(
                        Codec.STRING.optionalFieldOf("ability", "").forGetter(Charge::ability),
                        Codec.intRange(0, 64).optionalFieldOf("range", 0).forGetter(Charge::range))
                .apply(i, Charge::new));

        /** Whether it is on. */
        public boolean on() {
            return !ability.isEmpty() && range > 0;
        }
    }

    /**
     * A soldier of {@code rank} or more is spared: it is pulled back from a retreat first, and once its health falls
     * under {@code below} (a fraction) it leaves the fight, for a temple or a civic centre within {@code near} blocks
     * to heal in, or else for the healers behind the army, and returns at {@code resume}.
     */
    public record Veterans(int rank, double below, double resume, int near) {
        public static final Veterans NONE = new Veterans(0, 0, 0, 0);

        static final Codec<Veterans> CODEC = RecordCodecBuilder.create(i -> i.group(
                        Codec.intRange(0, 16).optionalFieldOf("rank", 0).forGetter(Veterans::rank),
                        Codec.doubleRange(0, 1).optionalFieldOf("below", 0.0).forGetter(Veterans::below),
                        Codec.doubleRange(0, 1).optionalFieldOf("resume", 0.0).forGetter(Veterans::resume),
                        Codec.intRange(0, 256).optionalFieldOf("near", 0).forGetter(Veterans::near))
                .apply(i, Veterans::new));

        /** Whether it is on. */
        public boolean on() {
            return rank > 0;
        }

        /** Whether {@code rank} is a veteran's. */
        public boolean veteran(int rank) {
            return on() && rank >= this.rank;
        }
    }

    static final Codec<Depth> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Techs.CODEC.optionalFieldOf("techs", Techs.NONE).forGetter(Depth::techs),
                    Codec.intRange(0, 16).optionalFieldOf("healers", 0).forGetter(Depth::healers),
                    Charge.CODEC.optionalFieldOf("charge", Charge.NONE).forGetter(Depth::charge),
                    Codec.intRange(0, 512).optionalFieldOf("march", 0).forGetter(Depth::march),
                    Veterans.CODEC.optionalFieldOf("veterans", Veterans.NONE).forGetter(Depth::veterans),
                    Codec.intRange(0, 100_000).optionalFieldOf("hold", 0).forGetter(Depth::hold),
                    Codec.intRange(0, 100_000).optionalFieldOf("techHold", 0).forGetter(Depth::techHold),
                    Codec.intRange(0, 100_000).optionalFieldOf("phaseHold", 0).forGetter(Depth::phaseHold),
                    Codec.unboundedMap(Codec.STRING, Codec.intRange(0, 200))
                            .optionalFieldOf("workers", new TreeMap<>())
                            .forGetter(Depth::workers))
            .apply(i, Depth::new));
}
