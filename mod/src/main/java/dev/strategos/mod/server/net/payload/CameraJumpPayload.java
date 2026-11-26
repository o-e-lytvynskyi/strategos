package dev.strategos.mod.server.net.payload;

import dev.strategos.core.protocol.CameraJump;
import dev.strategos.core.protocol.WireCodec;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.ByteBufWire;
import dev.strategos.mod.server.net.MatchScopedPayload;
import dev.strategos.mod.server.net.PayloadSpec;
import dev.strategos.mod.server.net.SessionEnvelope;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.Nullable;

/**
 * {@code strategos:camera_jump}, client to server (CAM-03): a commander's camera to a far point, answered by a teleport
 * at the start of the server's next tick; at most 4 a second, inside the match space plus 32 blocks.
 */
public record CameraJumpPayload(long epoch, @Nullable CameraJump jump) implements MatchScopedPayload {
    public CameraJumpPayload(@Nullable CameraJump jump) {
        this(0, jump);
    }

    private static final WireCodec<SessionEnvelope<CameraJump>> WIRE = SessionEnvelope.codec(CameraJump.WIRE);
    public static final Type<CameraJumpPayload> TYPE = new Type<>(Ids.id("camera_jump"));
    public static final PayloadSpec SPEC = PayloadSpec.toServer(TYPE.id(), WIRE.maxSize());
    public static final StreamCodec<RegistryFriendlyByteBuf, CameraJumpPayload> STREAM_CODEC = ByteBufWire.serverCodec(
            WIRE,
            frame -> new CameraJumpPayload(frame.epoch(), frame.body()),
            p -> p.jump == null ? null : new SessionEnvelope<>(p.epoch, p.jump),
            () -> new CameraJumpPayload(0, null));

    @Override
    public boolean decoded() {
        return jump != null;
    }

    @Override
    public CameraJumpPayload withEpoch(long epoch) {
        return new CameraJumpPayload(epoch, jump);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
