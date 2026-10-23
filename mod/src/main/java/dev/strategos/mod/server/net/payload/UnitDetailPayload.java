package dev.strategos.mod.server.net.payload;

import dev.strategos.core.protocol.UnitDetail;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.Audience;
import dev.strategos.mod.server.net.ByteBufWire;
import dev.strategos.mod.server.net.PayloadSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** {@code strategos:unit_detail}, server to client (NET-08): the details a player's {@code ui_query} asked for, own units only, on change and at most ten a second; to that player only. */
public record UnitDetailPayload(UnitDetail detail) implements CustomPacketPayload {
    public static final Type<UnitDetailPayload> TYPE = new Type<>(Ids.id("unit_detail"));
    public static final PayloadSpec SPEC =
            PayloadSpec.toClient(TYPE.id(), UnitDetail.WIRE.maxSize(), Audience.Kind.PLAYER);
    public static final StreamCodec<RegistryFriendlyByteBuf, UnitDetailPayload> STREAM_CODEC =
            ByteBufWire.codec(UnitDetail.WIRE, UnitDetailPayload::new, UnitDetailPayload::detail);

    @Override
    public Type<UnitDetailPayload> type() {
        return TYPE;
    }
}
