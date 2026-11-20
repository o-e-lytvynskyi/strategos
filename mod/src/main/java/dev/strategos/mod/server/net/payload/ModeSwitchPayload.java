package dev.strategos.mod.server.net.payload;

import dev.strategos.core.protocol.ModeSwitch;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.ByteBufWire;
import dev.strategos.mod.server.net.PayloadSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.Nullable;

/**
 * {@code strategos:mode_switch}, client to server (CAM-05): a commander to first person at a column of its match, or
 * back to the RTS view; at most one a second, with a 5 s cooldown between switches.
 */
public record ModeSwitchPayload(@Nullable ModeSwitch request) implements CustomPacketPayload {
    public static final Type<ModeSwitchPayload> TYPE = new Type<>(Ids.id("mode_switch"));
    public static final PayloadSpec SPEC = PayloadSpec.toServer(TYPE.id(), ModeSwitch.WIRE.maxSize());
    public static final StreamCodec<RegistryFriendlyByteBuf, ModeSwitchPayload> STREAM_CODEC = ByteBufWire.serverCodec(
            ModeSwitch.WIRE, ModeSwitchPayload::new, ModeSwitchPayload::request, () -> new ModeSwitchPayload(null));

    @Override
    public Type<ModeSwitchPayload> type() {
        return TYPE;
    }
}
