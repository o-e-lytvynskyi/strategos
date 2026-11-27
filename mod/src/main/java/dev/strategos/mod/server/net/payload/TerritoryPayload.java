package dev.strategos.mod.server.net.payload;

import dev.strategos.core.protocol.TerritoryUpdate;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.Audience;
import dev.strategos.mod.server.net.ByteBufWire;
import dev.strategos.mod.server.net.PayloadSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * {@code strategos:territory}, server to client (TERR-01): one part of a territory version, the owner and connected bit
 * of each 8x8 tile; public, every player of the match gets it, one part a tick at most.
 */
public record TerritoryPayload(TerritoryUpdate update) implements CustomPacketPayload {
    public static final Type<TerritoryPayload> TYPE = new Type<>(Ids.id("territory"));
    public static final PayloadSpec SPEC =
            PayloadSpec.toClient(TYPE.id(), TerritoryUpdate.WIRE.maxSize(), Audience.Kind.MATCH, Audience.Kind.PLAYER);
    public static final StreamCodec<RegistryFriendlyByteBuf, TerritoryPayload> STREAM_CODEC =
            ByteBufWire.codec(TerritoryUpdate.WIRE, TerritoryPayload::new, TerritoryPayload::update);

    @Override
    public Type<TerritoryPayload> type() {
        return TYPE;
    }
}
