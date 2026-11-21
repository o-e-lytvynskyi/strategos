package dev.strategos.mod.server.net.payload;

import dev.strategos.core.protocol.ModeSwitch;
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
 * {@code strategos:mode_switch}, client to server (CAM-05): a commander to first person at a column of its match, or
 * back to the RTS view; at most one a second, with a 5 s cooldown between switches.
 */
public record ModeSwitchPayload(long epoch, @Nullable ModeSwitch request) implements MatchScopedPayload {
    public ModeSwitchPayload(@Nullable ModeSwitch request) {
        this(0, request);
    }

    private static final WireCodec<SessionEnvelope<ModeSwitch>> WIRE = SessionEnvelope.codec(ModeSwitch.WIRE);
    public static final Type<ModeSwitchPayload> TYPE = new Type<>(Ids.id("mode_switch"));
    public static final PayloadSpec SPEC = PayloadSpec.toServer(TYPE.id(), WIRE.maxSize());
    public static final StreamCodec<RegistryFriendlyByteBuf, ModeSwitchPayload> STREAM_CODEC = ByteBufWire.serverCodec(
            WIRE,
            frame -> new ModeSwitchPayload(frame.epoch(), frame.body()),
            p -> p.request == null ? null : new SessionEnvelope<>(p.epoch, p.request),
            () -> new ModeSwitchPayload(0, null));

    @Override
    public boolean decoded() {
        return request != null;
    }

    @Override
    public ModeSwitchPayload withEpoch(long epoch) {
        return new ModeSwitchPayload(epoch, request);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
