package dev.strategos.mod.server.net.payload;

import dev.strategos.core.protocol.VisionDelta;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.Audience;
import dev.strategos.mod.server.net.ByteBufWire;
import dev.strategos.mod.server.net.PayloadSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * {@code strategos:vision_delta}, server to client (FOW-07): the viewer's faction's visible and explored cells,
 * run-length encoded, at 2 Hz.
 */
public record VisionDeltaPayload(VisionDelta delta) implements CustomPacketPayload {
    public static final Type<VisionDeltaPayload> TYPE = new Type<>(Ids.id("vision_delta"));
    public static final PayloadSpec SPEC =
            PayloadSpec.toClient(TYPE.id(), VisionDelta.WIRE.maxSize(), Audience.Kind.FACTION);
    public static final StreamCodec<RegistryFriendlyByteBuf, VisionDeltaPayload> STREAM_CODEC =
            ByteBufWire.codec(VisionDelta.WIRE, VisionDeltaPayload::new, VisionDeltaPayload::delta);

    @Override
    public Type<VisionDeltaPayload> type() {
        return TYPE;
    }
}
