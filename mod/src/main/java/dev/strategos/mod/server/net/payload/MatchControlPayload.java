package dev.strategos.mod.server.net.payload;

import dev.strategos.core.protocol.MatchControl;
import dev.strategos.core.protocol.WireCodec;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.ByteBufWire;
import dev.strategos.mod.server.net.MatchScopedPayload;
import dev.strategos.mod.server.net.PayloadSpec;
import dev.strategos.mod.server.net.SessionEnvelope;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.Nullable;

/**
 * {@code strategos:match_control}, client to server (MATCH-05): a seated player pauses or resumes its match, or sets
 * the game's speed, in singleplayer only.
 */
public record MatchControlPayload(long epoch, @Nullable MatchControl control) implements MatchScopedPayload {
    public MatchControlPayload(@Nullable MatchControl control) {
        this(0, control);
    }

    private static final WireCodec<SessionEnvelope<MatchControl>> WIRE = SessionEnvelope.codec(MatchControl.WIRE);
    public static final Type<MatchControlPayload> TYPE = new Type<>(Ids.id("match_control"));
    public static final PayloadSpec SPEC = PayloadSpec.toServer(TYPE.id(), WIRE.maxSize());
    public static final StreamCodec<RegistryFriendlyByteBuf, MatchControlPayload> STREAM_CODEC =
            ByteBufWire.serverCodec(
                    WIRE,
                    frame -> new MatchControlPayload(frame.epoch(), frame.body()),
                    p -> p.control == null ? null : new SessionEnvelope<>(p.epoch, p.control),
                    () -> new MatchControlPayload(0, null));

    @Override
    public boolean decoded() {
        return control != null;
    }

    @Override
    public MatchControlPayload withEpoch(long epoch) {
        return new MatchControlPayload(epoch, control);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
