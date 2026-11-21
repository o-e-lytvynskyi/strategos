package dev.strategos.mod.server.net.payload;

import dev.strategos.core.protocol.DebugSubscribe;
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
 * {@code strategos:debug_subscribe}, client to server, optional (PATH-10): the overlay layers an operator wants and its
 * area; no layer at all ends the subscription.
 */
public record DebugSubscribePayload(long epoch, @Nullable DebugSubscribe request) implements MatchScopedPayload {
    public DebugSubscribePayload(@Nullable DebugSubscribe request) {
        this(0, request);
    }

    private static final WireCodec<SessionEnvelope<DebugSubscribe>> WIRE = SessionEnvelope.codec(DebugSubscribe.WIRE);
    public static final Type<DebugSubscribePayload> TYPE = new Type<>(Ids.id("debug_subscribe"));
    public static final PayloadSpec SPEC = PayloadSpec.toServer(TYPE.id(), WIRE.maxSize());
    public static final StreamCodec<RegistryFriendlyByteBuf, DebugSubscribePayload> STREAM_CODEC =
            ByteBufWire.serverCodec(
                    WIRE,
                    frame -> new DebugSubscribePayload(frame.epoch(), frame.body()),
                    p -> p.request == null ? null : new SessionEnvelope<>(p.epoch, p.request),
                    () -> new DebugSubscribePayload(0, null));

    @Override
    public boolean decoded() {
        return request != null;
    }

    @Override
    public DebugSubscribePayload withEpoch(long epoch) {
        return new DebugSubscribePayload(epoch, request);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
