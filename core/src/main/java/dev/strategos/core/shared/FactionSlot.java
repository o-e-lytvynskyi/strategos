package dev.strategos.core.shared;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/** A faction's seat in a match: 0..15. */
public record FactionSlot(int value) {
    public static final int MAX = 15;
    public static final Codec<FactionSlot> CODEC = Codec.INT.comapFlatMap(FactionSlot::read, FactionSlot::value);

    public FactionSlot {
        if (value < 0 || value > MAX) {
            throw new IllegalArgumentException("faction slot " + value + " outside 0.." + MAX);
        }
    }

    private static DataResult<FactionSlot> read(int value) {
        return value < 0 || value > MAX
                ? DataResult.error(() -> "faction slot " + value + " outside 0.." + MAX)
                : DataResult.success(new FactionSlot(value));
    }
}
