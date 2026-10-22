package dev.strategos.mod.server.net.payload;

import dev.strategos.core.protocol.FeedbackMsg;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.Audience;
import dev.strategos.mod.server.net.ByteBufWire;
import dev.strategos.mod.server.net.PayloadSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** {@code strategos:feedback}, server to a player (its command's result) or a faction (a notice). */
public record FeedbackPayload(FeedbackMsg message) implements CustomPacketPayload {
    public static final Type<FeedbackPayload> TYPE = new Type<>(Ids.id("feedback"));
    public static final PayloadSpec SPEC =
            PayloadSpec.toClient(TYPE.id(), FeedbackMsg.WIRE.maxSize(), Audience.Kind.PLAYER, Audience.Kind.FACTION);
    public static final StreamCodec<RegistryFriendlyByteBuf, FeedbackPayload> STREAM_CODEC =
            ByteBufWire.codec(FeedbackMsg.WIRE, FeedbackPayload::new, FeedbackPayload::message);

    @Override
    public Type<FeedbackPayload> type() {
        return TYPE;
    }
}
