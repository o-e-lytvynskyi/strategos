package dev.strategos.core.shared;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/** A match's id within one server: 1 and up. */
public record MatchId(int value) {
    public static final Codec<MatchId> CODEC = Codec.INT.comapFlatMap(MatchId::read, MatchId::value);

    public MatchId {
        if (value < 1) {
            throw new IllegalArgumentException("match id " + value + " must be at least 1");
        }
    }

    private static DataResult<MatchId> read(int value) {
        return value < 1
                ? DataResult.error(() -> "match id " + value + " must be at least 1")
                : DataResult.success(new MatchId(value));
    }

    @Override
    public String toString() {
        return "m" + value;
    }
}
