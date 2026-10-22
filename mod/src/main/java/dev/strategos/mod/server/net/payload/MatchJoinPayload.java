package dev.strategos.mod.server.net.payload;

import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.Audience;
import dev.strategos.mod.server.net.ByteBufWire;
import dev.strategos.mod.server.net.PayloadSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** {@code strategos:match_join}, server to one player: the match it now sits in, and its slot. */
public record MatchJoinPayload(MatchJoin join) implements CustomPacketPayload {
    public static final Type<MatchJoinPayload> TYPE = new Type<>(Ids.id("match_join"));
    public static final PayloadSpec SPEC =
            PayloadSpec.toClient(TYPE.id(), MatchJoin.WIRE.maxSize(), Audience.Kind.PLAYER);
    public static final StreamCodec<RegistryFriendlyByteBuf, MatchJoinPayload> STREAM_CODEC =
            ByteBufWire.codec(MatchJoin.WIRE, MatchJoinPayload::new, MatchJoinPayload::join);

    @Override
    public Type<MatchJoinPayload> type() {
        return TYPE;
    }
}
