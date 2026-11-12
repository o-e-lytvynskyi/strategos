package dev.strategos.mod.server.net.payload;

import dev.strategos.core.protocol.Fx;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.Audience;
import dev.strategos.mod.server.net.ByteBufWire;
import dev.strategos.mod.server.net.PayloadSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * {@code strategos:fx}, server to client (AUD-01): events to show or sound, at most 64; only to the players who track
 * the unit, those whose faction sees the cell, or either end of a shot, or one player.
 */
public record FxPayload(Fx fx) implements CustomPacketPayload {
    public static final Type<FxPayload> TYPE = new Type<>(Ids.id("fx"));
    public static final PayloadSpec SPEC = PayloadSpec.toClient(
            TYPE.id(),
            Fx.WIRE.maxSize(),
            Audience.Kind.TRACKERS_OF,
            Audience.Kind.SEES_CELL,
            Audience.Kind.SEES_EITHER,
            Audience.Kind.PLAYER);
    public static final StreamCodec<RegistryFriendlyByteBuf, FxPayload> STREAM_CODEC =
            ByteBufWire.codec(Fx.WIRE, FxPayload::new, FxPayload::fx);

    @Override
    public Type<FxPayload> type() {
        return TYPE;
    }
}
