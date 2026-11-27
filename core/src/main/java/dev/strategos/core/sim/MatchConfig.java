package dev.strategos.core.sim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.content.StartSlot;
import dev.strategos.core.shared.CivId;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.Hex64;
import dev.strategos.core.shared.MatchId;
import java.util.List;

/**
 * What a match is created from: its id, seed, space, faction count (1..16), rules and the map's start slots, which
 * every computer player knows, fog or not (AI-18); empty for a match on no map, and then absent from the encoding, so
 * journals and saves from before them read unchanged. {@code civs} is each faction's civilisation (CIV-01, index =
 * faction): empty means every faction plays {@link CivId#DEFAULT}, and is then absent from the encoding too.
 */
@CoreApi
public record MatchConfig(
        MatchId id,
        long seed,
        MatchBounds bounds,
        int factions,
        MatchRules rules,
        List<StartSlot> starts,
        List<CivId> civs) {
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
                            .forGetter(MatchConfig::starts),
                    CivId.CODEC.listOf().optionalFieldOf("civs", List.of()).forGetter(MatchConfig::civs))
            .apply(i, MatchConfig::new));

    /** This config with {@code rules}: every other component, the civilisations included, as it was. */
    public MatchConfig withRules(MatchRules rules) {
        return new MatchConfig(id, seed, bounds, factions, rules, starts, civs);
    }

    /**
     * A match whose factions all play the default civilisation; for tests and tools. Code that builds a config for a
     * real match passes {@code civs} explicitly, so a civilisation is never dropped by a copy.
     */
    public MatchConfig(
            MatchId id, long seed, MatchBounds bounds, int factions, MatchRules rules, List<StartSlot> starts) {
        this(id, seed, bounds, factions, rules, starts, List.of());
    }

    /** A match on no map: no start slots. */
    public MatchConfig(MatchId id, long seed, MatchBounds bounds, int factions, MatchRules rules) {
        this(id, seed, bounds, factions, rules, List.of(), List.of());
    }

    public MatchConfig {
        starts = List.copyOf(starts);
        civs = List.copyOf(civs);
        if (factions < 1 || factions > MAX_FACTIONS) {
            throw new IllegalArgumentException("factions " + factions + " outside 1.." + MAX_FACTIONS);
        }
        if (!civs.isEmpty() && civs.size() != factions) {
            throw new IllegalArgumentException(
                    "civs lists " + civs.size() + " civilisations for " + factions + " factions");
        }
    }

    /** Faction {@code faction}'s civilisation: its entry, or the default when the list is empty. */
    public CivId civ(int faction) {
        return civs.isEmpty() ? CivId.DEFAULT : civs.get(faction);
    }

    /** Whether some faction plays a civilisation other than the default. */
    public boolean hasCustomCivs() {
        for (int i = 0; i < civs.size(); i++) { // an index loop: no iterator per hash
            if (!civs.get(i).equals(CivId.DEFAULT)) {
                return true;
            }
        }
        return false;
    }
}
