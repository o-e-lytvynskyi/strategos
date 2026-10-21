package dev.strategos.core.sim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.CoreApi;

/** The match's limits: commands applied per tick (the rest wait) and live units. */
@CoreApi
public record MatchRules(int commandsPerTick, int unitCap) {
    public static final MatchRules DEFAULTS = new MatchRules(256, 4096);

    public static final Codec<MatchRules> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.intRange(1, Integer.MAX_VALUE)
                            .fieldOf("commandsPerTick")
                            .forGetter(MatchRules::commandsPerTick),
                    Codec.intRange(1, Integer.MAX_VALUE).fieldOf("unitCap").forGetter(MatchRules::unitCap))
            .apply(i, MatchRules::new));

    public MatchRules {
        if (commandsPerTick < 1 || unitCap < 1) {
            throw new IllegalArgumentException("rules need positive limits: " + commandsPerTick + ", " + unitCap);
        }
    }
}
