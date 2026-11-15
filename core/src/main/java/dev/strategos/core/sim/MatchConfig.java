package dev.strategos.core.sim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.content.StartSlot;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.Hex64;
import dev.strategos.core.shared.MatchId;
import java.util.List;

/**
 * What a match is created from: its id, seed, space, faction count (1..16), rules and the map's start slots, which
 * every computer player knows, fog or not (AI-18); empty for a match on no map, and then absent from the encoding, so
 * journals and saves from before them read unchanged.
 */
@CoreApi
public record MatchConfig(
        MatchId id, long seed, MatchBounds bounds, int factions, MatchRules rules, List<StartSlot> starts) {
    public static final int MAX_FACTIONS = 16;

    public static final Codec<MatchConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
                    MatchId.CODEC.fieldOf("id").forGetter(MatchConfig::id),
                    Hex64.CODEC.fieldOf("seed").forGetter(MatchConfig::seed),
                    MatchBounds.CODEC.fieldOf("bounds").forGetter(MatchConfig::bounds),
                    Codec.intRange(1, MAX_FACTIONS).fieldOf("factions").forGetter(MatchConfig::factions),
                    MatchRules.CODEC.fieldOf("rules").forGetter(MatchConfig::rules),
                    StartSlot.CODEC
                            .listOf()
                            .optionalFieldOf("starts", List.of())
                            .forGetter(MatchConfig::starts))
            .apply(i, MatchConfig::new));

    /** A match on no map: no start slots. */
    public MatchConfig(MatchId id, long seed, MatchBounds bounds, int factions, MatchRules rules) {
        this(id, seed, bounds, factions, rules, List.of());
    }

    public MatchConfig {
        starts = List.copyOf(starts);
        if (factions < 1 || factions > MAX_FACTIONS) {
            throw new IllegalArgumentException("factions " + factions + " outside 1.." + MAX_FACTIONS);
        }
    }
}
