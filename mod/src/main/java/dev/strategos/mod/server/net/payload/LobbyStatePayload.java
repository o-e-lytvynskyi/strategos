package dev.strategos.mod.server.net.payload;

import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.Audience;
import dev.strategos.mod.server.net.ByteBufWire;
import dev.strategos.mod.server.net.LobbyState;
import dev.strategos.mod.server.net.PayloadSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** One admitted lobby member's private projection, never a match-wide or tracking audience. */
public record LobbyStatePayload(LobbyState state) implements CustomPacketPayload {
    public static final Type<LobbyStatePayload> TYPE = new Type<>(Ids.id("lobby_state"));
    public static final PayloadSpec SPEC =
            PayloadSpec.toClient(TYPE.id(), LobbyState.WIRE.maxSize(), Audience.Kind.PLAYER);
    public static final StreamCodec<RegistryFriendlyByteBuf, LobbyStatePayload> STREAM_CODEC =
            ByteBufWire.codec(LobbyState.WIRE, LobbyStatePayload::new, LobbyStatePayload::state);

    @Override
    public Type<LobbyStatePayload> type() {
        return TYPE;
    }
}
