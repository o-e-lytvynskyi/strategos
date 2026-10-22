package dev.strategos.mod.server.net.payload;

import dev.strategos.core.protocol.DebugSubscribe;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.ByteBufWire;
import dev.strategos.mod.server.net.PayloadSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * {@code strategos:debug_subscribe}, client to server, optional (PATH-10): the overlay layers an operator wants and its
 * area; no layer at all ends the subscription.
 */
public record DebugSubscribePayload(DebugSubscribe request) implements CustomPacketPayload {
    public static final Type<DebugSubscribePayload> TYPE = new Type<>(Ids.id("debug_subscribe"));
    public static final PayloadSpec SPEC = PayloadSpec.toServer(TYPE.id(), DebugSubscribe.WIRE.maxSize());
    public static final StreamCodec<RegistryFriendlyByteBuf, DebugSubscribePayload> STREAM_CODEC =
            ByteBufWire.codec(DebugSubscribe.WIRE, DebugSubscribePayload::new, DebugSubscribePayload::request);

    @Override
    public Type<DebugSubscribePayload> type() {
        return TYPE;
    }
}
