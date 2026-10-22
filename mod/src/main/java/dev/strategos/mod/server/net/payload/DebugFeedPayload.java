package dev.strategos.mod.server.net.payload;

import dev.strategos.core.protocol.DebugFeedMsg;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.Audience;
import dev.strategos.mod.server.net.ByteBufWire;
import dev.strategos.mod.server.net.PayloadSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * {@code strategos:debug_feed}, server to one subscribed operator, optional (PATH-10): one layer of the movement
 * overlay.
 */
public record DebugFeedPayload(DebugFeedMsg message) implements CustomPacketPayload {
    public static final Type<DebugFeedPayload> TYPE = new Type<>(Ids.id("debug_feed"));
    public static final PayloadSpec SPEC =
            PayloadSpec.toClient(TYPE.id(), DebugFeedMsg.WIRE.maxSize(), Audience.Kind.PLAYER);
    public static final StreamCodec<RegistryFriendlyByteBuf, DebugFeedPayload> STREAM_CODEC =
            ByteBufWire.codec(DebugFeedMsg.WIRE, DebugFeedPayload::new, DebugFeedPayload::message);

    @Override
    public Type<DebugFeedPayload> type() {
        return TYPE;
    }
}
