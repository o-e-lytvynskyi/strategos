package dev.strategos.core.sim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.Hex64;
import dev.strategos.core.shared.MatchId;

/** What a match is created from: its id, seed, space, faction count (1..16) and rules. */
@CoreApi
public record MatchConfig(MatchId id, long seed, MatchBounds bounds, int factions, MatchRules rules) {
    public static final int MAX_FACTIONS = 16;

    public static final Codec<MatchConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
                    MatchId.CODEC.fieldOf("id").forGetter(MatchConfig::id),
                    Hex64.CODEC.fieldOf("seed").forGetter(MatchConfig::seed),
                    MatchBounds.CODEC.fieldOf("bounds").forGetter(MatchConfig::bounds),
                    Codec.intRange(1, MAX_FACTIONS).fieldOf("factions").forGetter(MatchConfig::factions),
                    MatchRules.CODEC.fieldOf("rules").forGetter(MatchConfig::rules))
            .apply(i, MatchConfig::new));

    public MatchConfig {
        if (factions < 1 || factions > MAX_FACTIONS) {
            throw new IllegalArgumentException("factions " + factions + " outside 1.." + MAX_FACTIONS);
        }
    }
}
