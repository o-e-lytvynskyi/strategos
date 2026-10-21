package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Locale;

/** How an order joins a unit's queue. M0 accepts {@link #REPLACE} only. */
public enum QueueMode {
    REPLACE,
    QUEUE,
    PUSH_FRONT;

    /** Lower-case names, as in Minecraft's own data. */
    public static final Codec<QueueMode> CODEC = Codec.STRING.comapFlatMap(QueueMode::read, QueueMode::id);

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    private static DataResult<QueueMode> read(String id) {
        for (QueueMode mode : values()) {
            if (mode.id().equals(id)) {
                return DataResult.success(mode);
            }
        }
        return DataResult.error(() -> "unknown queue mode '" + id + "'");
    }
}
