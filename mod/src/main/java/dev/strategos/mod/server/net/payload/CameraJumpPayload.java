package dev.strategos.mod.server.net.payload;

import dev.strategos.core.protocol.CameraJump;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.ByteBufWire;
import dev.strategos.mod.server.net.PayloadSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * {@code strategos:camera_jump}, client to server (CAM-03): a commander's camera to a far point, answered by a teleport
 * at the start of the server's next tick; at most 4 a second, inside the match space plus 32 blocks.
 */
public record CameraJumpPayload(CameraJump jump) implements CustomPacketPayload {
    public static final Type<CameraJumpPayload> TYPE = new Type<>(Ids.id("camera_jump"));
    public static final PayloadSpec SPEC = PayloadSpec.toServer(TYPE.id(), CameraJump.WIRE.maxSize());
    public static final StreamCodec<RegistryFriendlyByteBuf, CameraJumpPayload> STREAM_CODEC =
            ByteBufWire.codec(CameraJump.WIRE, CameraJumpPayload::new, CameraJumpPayload::jump);

    @Override
    public Type<CameraJumpPayload> type() {
        return TYPE;
    }
}
