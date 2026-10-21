package dev.strategos.mod.server.match;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hex64;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.sim.MatchLifecycle;
import java.util.Locale;
import java.util.Optional;

/**
 * What the match registry blob keeps of a saved match: where it lives, its seed, its lifecycle, who made it, the save
 * epoch of its last blob, and why it faulted if it did. The match itself is in its own blob.
 */
public record MatchRecord(
        MatchId id,
        MatchSpace space,
        long seed,
        MatchLifecycle lifecycle,
        MatchSource source,
        long lastEpoch,
        Optional<String> faultMessage) {
    private static final Codec<MatchLifecycle> LIFECYCLE = byName(MatchLifecycle.values());
    private static final Codec<MatchSource> SOURCE = byName(MatchSource.values());

    public static final Codec<MatchRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
                    MatchId.CODEC.fieldOf("id").forGetter(MatchRecord::id),
                    MatchSpace.CODEC.fieldOf("space").forGetter(MatchRecord::space),
                    Hex64.CODEC.fieldOf("seed").forGetter(MatchRecord::seed),
                    LIFECYCLE.fieldOf("lifecycle").forGetter(MatchRecord::lifecycle),
                    SOURCE.fieldOf("source").forGetter(MatchRecord::source),
                    Codec.LONG.fieldOf("lastEpoch").forGetter(MatchRecord::lastEpoch),
                    Codec.STRING.optionalFieldOf("faultMessage").forGetter(MatchRecord::faultMessage))
            .apply(i, MatchRecord::new));

    public MatchRecord withLifecycle(MatchLifecycle lifecycle, Optional<String> faultMessage) {
        return new MatchRecord(id, space, seed, lifecycle, source, lastEpoch, faultMessage);
    }

    public MatchRecord withLastEpoch(long lastEpoch) {
        return new MatchRecord(id, space, seed, lifecycle, source, lastEpoch, faultMessage);
    }

    /** An enum by its lower-case name, never its ordinal, so reordering constants cannot change a save. */
    private static <E extends Enum<E>> Codec<E> byName(E[] values) {
        return Codec.STRING.comapFlatMap(
                name -> {
                    for (E value : values) {
                        if (value.name().toLowerCase(Locale.ROOT).equals(name)) {
                            return DataResult.success(value);
                        }
                    }
                    return DataResult.error(() -> "unknown value '" + name + "'");
                },
                value -> value.name().toLowerCase(Locale.ROOT));
    }
}
