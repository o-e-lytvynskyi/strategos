package dev.strategos.mod.server.net.payload;

import dev.strategos.core.protocol.PlayerAuthority;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.Audience;
import dev.strategos.mod.server.net.ByteBufWire;
import dev.strategos.mod.server.net.PayloadSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Only the authenticated recipient receives its current command rights and primary perspective. */
public record PlayerAuthorityPayload(PlayerAuthority authority) implements CustomPacketPayload {
    public static final Type<PlayerAuthorityPayload> TYPE = new Type<>(Ids.id("player_authority"));
    public static final PayloadSpec SPEC =
            PayloadSpec.toClient(TYPE.id(), PlayerAuthority.WIRE.maxSize(), Audience.Kind.PLAYER);
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerAuthorityPayload> STREAM_CODEC =
            ByteBufWire.codec(PlayerAuthority.WIRE, PlayerAuthorityPayload::new, PlayerAuthorityPayload::authority);

    @Override
    public Type<PlayerAuthorityPayload> type() {
        return TYPE;
    }
}
