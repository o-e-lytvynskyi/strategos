package dev.strategos.mod.server.net.payload;

import dev.strategos.core.protocol.SkirmishSetup;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.ByteBufWire;
import dev.strategos.mod.server.net.PayloadSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * {@code strategos:skirmish}, client to server (MATCH-02): the setup screen's choice, sent once the arena world is
 * joined; the server makes the match in the arena's next slot and seats the sender in slot 0.
 */
public record SkirmishPayload(SkirmishSetup setup) implements CustomPacketPayload {
    public static final Type<SkirmishPayload> TYPE = new Type<>(Ids.id("skirmish"));
    public static final PayloadSpec SPEC = PayloadSpec.toServer(TYPE.id(), SkirmishSetup.WIRE.maxSize());
    public static final StreamCodec<RegistryFriendlyByteBuf, SkirmishPayload> STREAM_CODEC =
            ByteBufWire.codec(SkirmishSetup.WIRE, SkirmishPayload::new, SkirmishPayload::setup);

    @Override
    public Type<SkirmishPayload> type() {
        return TYPE;
    }
}
