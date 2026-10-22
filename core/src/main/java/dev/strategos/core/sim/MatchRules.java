package dev.strategos.core.sim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.sim.nav.DeliveryMode;
import java.util.function.Function;

/**
 * The match's limits: commands applied per tick (the rest wait) and live units; and the path service's rules (spec
 * §2.6): when results are delivered and {@code pathK} ticks after their request, {@code pageK} ticks for a page
 * rebuild, the admission window W in expansions per tick, the requests that may wait before {@code BUSY}, the ticks a
 * busy unit waits, the expansion caps of a search and a group join, the length of a move decided at once, and the
 * smallest group that shares a path. Each path rule has its default in the codec, so M0's rules still decode.
 */
@CoreApi
public record MatchRules(
        int commandsPerTick,
        int unitCap,
        DeliveryMode delivery,
        int pathK,
        int pageK,
        int admissionW,
        int maxOutstanding,
        int busyRetry,
        int searchCap,
        int joinCap,
        int shortMove,
        int groupMin) {
    /**
     * The admission window W's default, in expansion caps a tick: 80 searches at the 20,000 cap. Spike S7 set it (M1
     * T22): behind 200 requests a 100-unit order's results arrive within 4 ticks at p95, with the main thread's path work
     * at most 0.6 ms at p99 on one to four threads and on the slow-host proxy.
     */
    public static final int DEFAULT_W = 1_600_000;

    public static final MatchRules DEFAULTS = new MatchRules(256, 4096);

    public static final Codec<MatchRules> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.intRange(1, Integer.MAX_VALUE)
                            .fieldOf("commandsPerTick")
                            .forGetter(MatchRules::commandsPerTick),
                    Codec.intRange(1, Integer.MAX_VALUE).fieldOf("unitCap").forGetter(MatchRules::unitCap),
                    DeliveryMode.CODEC
                            .optionalFieldOf("delivery", DeliveryMode.STRICT)
                            .forGetter(MatchRules::delivery),
                    positive("pathK", 2, MatchRules::pathK),
                    positive("pageK", 4, MatchRules::pageK),
                    positive("admissionW", DEFAULT_W, MatchRules::admissionW),
                    positive("maxOutstanding", 2_048, MatchRules::maxOutstanding),
                    positive("busyRetry", 10, MatchRules::busyRetry),
                    positive("searchCap", 20_000, MatchRules::searchCap),
                    positive("joinCap", 2_000, MatchRules::joinCap),
                    positive("shortMove", 24, MatchRules::shortMove),
                    positive("groupMin", 8, MatchRules::groupMin))
            .apply(i, MatchRules::new));

    public MatchRules {
        if (commandsPerTick < 1 || unitCap < 1) {
            throw new IllegalArgumentException("rules need positive limits: " + commandsPerTick + ", " + unitCap);
        }
        if (pathK < 1
                || pageK < 1
                || admissionW < 1
                || maxOutstanding < 1
                || busyRetry < 1
                || searchCap < 1
                || joinCap < 1
                || shortMove < 1
                || groupMin < 1) {
            throw new IllegalArgumentException("path rules need positive values");
        }
        if (searchCap > admissionW || joinCap > admissionW) {
            throw new IllegalArgumentException("a search's cap " + Math.max(searchCap, joinCap)
                    + " does not fit the admission window " + admissionW + ", so it would never be admitted");
        }
    }

    /** M0's two limits with the path rules' defaults. */
    public MatchRules(int commandsPerTick, int unitCap) {
        this(commandsPerTick, unitCap, DeliveryMode.STRICT, 2, 4, DEFAULT_W, 2_048, 10, 20_000, 2_000, 24, 8);
    }

    /** These rules with another expansion cap per search. */
    public MatchRules withSearchCap(int cap) {
        return new MatchRules(
                commandsPerTick,
                unitCap,
                delivery,
                pathK,
                pageK,
                admissionW,
                maxOutstanding,
                busyRetry,
                cap,
                joinCap,
                shortMove,
                groupMin);
    }

    private static <O> RecordCodecBuilder<O, Integer> positive(String name, int fallback, Function<O, Integer> getter) {
        return Codec.intRange(1, Integer.MAX_VALUE)
                .optionalFieldOf(name, fallback)
                .forGetter(getter);
    }
}
