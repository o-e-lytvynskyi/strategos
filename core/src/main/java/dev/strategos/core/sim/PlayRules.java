package dev.strategos.core.sim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.CoreApi;
import java.util.Locale;

/**
 * How a match is won and what its players see (MATCH-01, MATCH-07): the victory rule; whether the map is revealed;
 * whether every cell starts explored; and whether AI players see through fog. Copied into the match's rules at creation
 * and changed only by a journaled {@code RuleChange}.
 *
 * <p>Setup visibility options: Fog (default: neither flag), Explored ({@code explored}), Revealed ({@code revealMap}).
 */
@CoreApi
public record PlayRules(Victory victory, boolean revealMap, boolean explored, boolean aiVision) {
    /** How a match ends. */
    @CoreApi
    public enum Victory {
        /** It never ends by itself: tests and sandboxes. */
        NONE,
        /** A faction whose last civic centre falls, or who resigns, loses; the last one standing wins (§1.6 row 18). */
        CIVIC_CENTRE,
        /** A faction with no unit and no building but fields and decorations left loses (M6 §2.3). */
        CONQUEST;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Victory of(String id) {
            for (Victory v : values()) {
                if (v.id().equals(id)) {
                    return v;
                }
            }
            throw new IllegalArgumentException("no victory rule '" + id + "'");
        }
    }

    public static final PlayRules DEFAULTS = new PlayRules(Victory.NONE, false, false, false);

    public static final Codec<PlayRules> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.STRING
                            .xmap(Victory::of, Victory::id)
                            .optionalFieldOf("victory", Victory.NONE)
                            .forGetter(PlayRules::victory),
                    Codec.BOOL.optionalFieldOf("revealMap", false).forGetter(PlayRules::revealMap),
                    Codec.BOOL.optionalFieldOf("explored", false).forGetter(PlayRules::explored),
                    Codec.BOOL.optionalFieldOf("aiVision", false).forGetter(PlayRules::aiVision))
            .apply(i, PlayRules::new));
}
