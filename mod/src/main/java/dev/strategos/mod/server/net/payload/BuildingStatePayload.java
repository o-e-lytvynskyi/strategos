package dev.strategos.mod.server.net.payload;

import dev.strategos.core.protocol.BuildingState;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.Audience;
import dev.strategos.mod.server.net.ByteBufWire;
import dev.strategos.mod.server.net.PayloadSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * {@code strategos:building_state}, server to client (BLD-01): the buildings a player knows of, its own faction's with
 * the owner's fields; to each player, when it changes.
 */
public record BuildingStatePayload(BuildingState state) implements CustomPacketPayload {
    public static final Type<BuildingStatePayload> TYPE = new Type<>(Ids.id("building_state"));
    public static final PayloadSpec SPEC =
            PayloadSpec.toClient(TYPE.id(), BuildingState.WIRE.maxSize(), Audience.Kind.PLAYER);
    public static final StreamCodec<RegistryFriendlyByteBuf, BuildingStatePayload> STREAM_CODEC =
            ByteBufWire.codec(BuildingState.WIRE, BuildingStatePayload::new, BuildingStatePayload::state);

    @Override
    public Type<BuildingStatePayload> type() {
        return TYPE;
    }
}
