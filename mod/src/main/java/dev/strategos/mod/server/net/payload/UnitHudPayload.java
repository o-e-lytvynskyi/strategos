package dev.strategos.mod.server.net.payload;

import dev.strategos.core.protocol.UnitHud;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.Audience;
import dev.strategos.mod.server.net.ByteBufWire;
import dev.strategos.mod.server.net.PayloadSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** {@code strategos:unit_hud}, server to client (NET-08, CMB-06): the units whose hit points changed for this player, at most five a second; to that player only. */
public record UnitHudPayload(UnitHud hud) implements CustomPacketPayload {
    public static final Type<UnitHudPayload> TYPE = new Type<>(Ids.id("unit_hud"));
    public static final PayloadSpec SPEC =
            PayloadSpec.toClient(TYPE.id(), UnitHud.WIRE.maxSize(), Audience.Kind.PLAYER);
    public static final StreamCodec<RegistryFriendlyByteBuf, UnitHudPayload> STREAM_CODEC =
            ByteBufWire.codec(UnitHud.WIRE, UnitHudPayload::new, UnitHudPayload::hud);

    @Override
    public Type<UnitHudPayload> type() {
        return TYPE;
    }
}
