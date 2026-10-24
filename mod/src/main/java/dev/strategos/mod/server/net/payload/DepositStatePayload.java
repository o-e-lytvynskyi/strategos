package dev.strategos.mod.server.net.payload;

import dev.strategos.core.protocol.DepositState;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.Audience;
import dev.strategos.mod.server.net.ByteBufWire;
import dev.strategos.mod.server.net.PayloadSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * {@code strategos:deposit_state}, server to client (ECO-08): the match's mines and quarries with what each has left;
 * to each player, when it changes.
 */
public record DepositStatePayload(DepositState state) implements CustomPacketPayload {
    public static final Type<DepositStatePayload> TYPE = new Type<>(Ids.id("deposit_state"));
    public static final PayloadSpec SPEC =
            PayloadSpec.toClient(TYPE.id(), DepositState.WIRE.maxSize(), Audience.Kind.PLAYER);
    public static final StreamCodec<RegistryFriendlyByteBuf, DepositStatePayload> STREAM_CODEC =
            ByteBufWire.codec(DepositState.WIRE, DepositStatePayload::new, DepositStatePayload::state);

    @Override
    public Type<DepositStatePayload> type() {
        return TYPE;
    }
}
