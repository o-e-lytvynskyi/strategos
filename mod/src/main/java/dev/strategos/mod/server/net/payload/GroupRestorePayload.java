package dev.strategos.mod.server.net.payload;

import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.Audience;
import dev.strategos.mod.server.net.ByteBufWire;
import dev.strategos.mod.server.net.GroupRestoreFrame;
import dev.strategos.mod.server.net.PayloadSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Private full-join validation data, never a command-authority source. */
public record GroupRestorePayload(GroupRestoreFrame frame) implements CustomPacketPayload {
    public static final Type<GroupRestorePayload> TYPE = new Type<>(Ids.id("group_restore"));
    public static final PayloadSpec SPEC =
            PayloadSpec.toClient(TYPE.id(), GroupRestoreFrame.WIRE.maxSize(), Audience.Kind.PLAYER);
    public static final StreamCodec<RegistryFriendlyByteBuf, GroupRestorePayload> STREAM_CODEC =
            ByteBufWire.codec(GroupRestoreFrame.WIRE, GroupRestorePayload::new, GroupRestorePayload::frame);

    @Override
    public Type<GroupRestorePayload> type() {
        return TYPE;
    }
}
