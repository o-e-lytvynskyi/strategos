package dev.strategos.mod.server.net.payload;

import dev.strategos.core.protocol.MatchStateMsg;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.Audience;
import dev.strategos.mod.server.net.ByteBufWire;
import dev.strategos.mod.server.net.PayloadSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** {@code strategos:match_state}, server to a match's members: its lifecycle and last tick. */
public record MatchStatePayload(MatchStateMsg state) implements CustomPacketPayload {
    public static final Type<MatchStatePayload> TYPE = new Type<>(Ids.id("match_state"));
    public static final PayloadSpec SPEC =
            PayloadSpec.toClient(TYPE.id(), MatchStateMsg.WIRE.maxSize(), Audience.Kind.MATCH);
    public static final StreamCodec<RegistryFriendlyByteBuf, MatchStatePayload> STREAM_CODEC =
            ByteBufWire.codec(MatchStateMsg.WIRE, MatchStatePayload::new, MatchStatePayload::state);

    @Override
    public Type<MatchStatePayload> type() {
        return TYPE;
    }
}
