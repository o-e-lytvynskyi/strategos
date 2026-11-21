package dev.strategos.mod.server.net.payload;

import dev.strategos.core.protocol.MatchPresentation;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.Audience;
import dev.strategos.mod.server.net.ByteBufWire;
import dev.strategos.mod.server.net.PayloadSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Public styles and human names, projected only for authenticated match recipients. */
public record MatchPresentationPayload(MatchPresentation presentation) implements CustomPacketPayload {
    public static final Type<MatchPresentationPayload> TYPE = new Type<>(Ids.id("match_presentation"));
    public static final PayloadSpec SPEC =
            PayloadSpec.toClient(TYPE.id(), MatchPresentation.WIRE.maxSize(), Audience.Kind.PLAYER);
    public static final StreamCodec<RegistryFriendlyByteBuf, MatchPresentationPayload> STREAM_CODEC = ByteBufWire.codec(
            MatchPresentation.WIRE, MatchPresentationPayload::new, MatchPresentationPayload::presentation);

    @Override
    public Type<MatchPresentationPayload> type() {
        return TYPE;
    }
}
