package dev.strategos.core.shared;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/** Who issues a command: {@link #ADMIN} (-1: server commands and tests) or a player's seat, 0..15. */
public record PlayerSlot(int value) {
    public static final PlayerSlot ADMIN = new PlayerSlot(-1);
    public static final Codec<PlayerSlot> CODEC = Codec.INT.comapFlatMap(PlayerSlot::read, PlayerSlot::value);

    public PlayerSlot {
        if (value < -1 || value > FactionSlot.MAX) {
            throw new IllegalArgumentException("player slot " + value + " outside -1.." + FactionSlot.MAX);
        }
    }

    public boolean isAdmin() {
        return value == -1;
    }

    /** ADMIN controls every faction; a player controls the faction of the same number (M0: one seat per faction). */
    public boolean controls(FactionSlot faction) {
        return isAdmin() || value == faction.value();
    }

    private static DataResult<PlayerSlot> read(int value) {
        return value < -1 || value > FactionSlot.MAX
                ? DataResult.error(() -> "player slot " + value + " outside -1.." + FactionSlot.MAX)
                : DataResult.success(new PlayerSlot(value));
    }
}
