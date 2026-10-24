package dev.strategos.mod.server.net.payload;

import dev.strategos.core.protocol.MatchControl;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.ByteBufWire;
import dev.strategos.mod.server.net.PayloadSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * {@code strategos:match_control}, client to server (MATCH-05): a seated player pauses or resumes its match, or sets
 * the game's speed, in singleplayer only.
 */
public record MatchControlPayload(MatchControl control) implements CustomPacketPayload {
    public static final Type<MatchControlPayload> TYPE = new Type<>(Ids.id("match_control"));
    public static final PayloadSpec SPEC = PayloadSpec.toServer(TYPE.id(), MatchControl.WIRE.maxSize());
    public static final StreamCodec<RegistryFriendlyByteBuf, MatchControlPayload> STREAM_CODEC =
            ByteBufWire.codec(MatchControl.WIRE, MatchControlPayload::new, MatchControlPayload::control);

    @Override
    public Type<MatchControlPayload> type() {
        return TYPE;
    }
}
