package dev.strategos.mod.server.net.payload;

import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.core.protocol.WireCodec;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.Audience;
import dev.strategos.mod.server.net.ByteBufWire;
import dev.strategos.mod.server.net.PayloadSpec;
import dev.strategos.mod.server.net.SessionEnvelope;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** {@code strategos:match_join}, server to one player: the match it now sits in, and its slot. */
public record MatchJoinPayload(long epoch, MatchJoin join) implements CustomPacketPayload {
    public MatchJoinPayload(MatchJoin join) {
        this(0, join);
    }

    private static final WireCodec<SessionEnvelope<MatchJoin>> WIRE = SessionEnvelope.codec(MatchJoin.WIRE);
    public static final Type<MatchJoinPayload> TYPE = new Type<>(Ids.id("match_join"));
    public static final PayloadSpec SPEC = PayloadSpec.toClient(TYPE.id(), WIRE.maxSize(), Audience.Kind.PLAYER);
    public static final StreamCodec<RegistryFriendlyByteBuf, MatchJoinPayload> STREAM_CODEC = ByteBufWire.codec(
            WIRE,
            frame -> new MatchJoinPayload(frame.epoch(), frame.body()),
            p -> new SessionEnvelope<>(p.epoch(), p.join()));

    @Override
    public Type<MatchJoinPayload> type() {
        return TYPE;
    }
}
