package dev.strategos.core.sim.nav;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import dev.strategos.core.shared.CoreApi;
import java.util.Locale;

/**
 * When path results reach their units (ADR-0015, 04 §9.3). {@code STRICT}: every job due at a tick is joined there, in
 * request-id order, so results never depend on thread timing. {@code ADAPTIVE} (PATH-16): the journal records which
 * results were ready, and a replay applies the same ones.
 */
@CoreApi
public enum DeliveryMode {
    STRICT,
    ADAPTIVE;

    public static final Codec<DeliveryMode> CODEC = Codec.STRING.comapFlatMap(DeliveryMode::read, DeliveryMode::id);

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    private static DataResult<DeliveryMode> read(String id) {
        for (DeliveryMode mode : values()) {
            if (mode.id().equals(id)) {
                return DataResult.success(mode);
            }
        }
        return DataResult.error(() -> "unknown delivery mode '" + id + "'");
    }
}
