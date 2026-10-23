package dev.strategos.mod.server.net.payload;

import dev.strategos.core.protocol.UiQuery;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.ByteBufWire;
import dev.strategos.mod.server.net.PayloadSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** {@code strategos:ui_query}, client to server (NET-08): the own units whose details a player wants, at most 32, at most four queries a second. */
public record UiQueryPayload(UiQuery query) implements CustomPacketPayload {
    public static final Type<UiQueryPayload> TYPE = new Type<>(Ids.id("ui_query"));
    public static final PayloadSpec SPEC = PayloadSpec.toServer(TYPE.id(), UiQuery.WIRE.maxSize());
    public static final StreamCodec<RegistryFriendlyByteBuf, UiQueryPayload> STREAM_CODEC =
            ByteBufWire.codec(UiQuery.WIRE, UiQueryPayload::new, UiQueryPayload::query);

    @Override
    public Type<UiQueryPayload> type() {
        return TYPE;
    }
}
