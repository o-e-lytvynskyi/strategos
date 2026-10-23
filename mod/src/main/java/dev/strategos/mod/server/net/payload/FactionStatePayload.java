package dev.strategos.mod.server.net.payload;

import dev.strategos.core.protocol.FactionState;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.Audience;
import dev.strategos.mod.server.net.ByteBufWire;
import dev.strategos.mod.server.net.PayloadSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * {@code strategos:faction_state}, server to client (ECO-01, UI-07): a faction's treasury, population and workers, when
 * they change and at most four times a second; to each player of that faction, never another's.
 */
public record FactionStatePayload(FactionState state) implements CustomPacketPayload {
    public static final Type<FactionStatePayload> TYPE = new Type<>(Ids.id("faction_state"));
    public static final PayloadSpec SPEC =
            PayloadSpec.toClient(TYPE.id(), FactionState.WIRE.maxSize(), Audience.Kind.PLAYER);
    public static final StreamCodec<RegistryFriendlyByteBuf, FactionStatePayload> STREAM_CODEC =
            ByteBufWire.codec(FactionState.WIRE, FactionStatePayload::new, FactionStatePayload::state);

    @Override
    public Type<FactionStatePayload> type() {
        return TYPE;
    }
}
