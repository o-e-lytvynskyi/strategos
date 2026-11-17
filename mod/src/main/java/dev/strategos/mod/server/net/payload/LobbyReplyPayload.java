package dev.strategos.mod.server.net.payload;

import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.Audience;
import dev.strategos.mod.server.net.ByteBufWire;
import dev.strategos.mod.server.net.LobbyReply;
import dev.strategos.mod.server.net.PayloadSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A player's own request failure, allowed before admission without revealing any lobby state. */
public record LobbyReplyPayload(LobbyReply reply) implements CustomPacketPayload {
    public static final Type<LobbyReplyPayload> TYPE = new Type<>(Ids.id("lobby_reply"));
    public static final PayloadSpec SPEC =
            PayloadSpec.toClient(TYPE.id(), LobbyReply.WIRE.maxSize(), Audience.Kind.PLAYER);
    public static final StreamCodec<RegistryFriendlyByteBuf, LobbyReplyPayload> STREAM_CODEC =
            ByteBufWire.codec(LobbyReply.WIRE, LobbyReplyPayload::new, LobbyReplyPayload::reply);

    @Override
    public Type<LobbyReplyPayload> type() {
        return TYPE;
    }
}
