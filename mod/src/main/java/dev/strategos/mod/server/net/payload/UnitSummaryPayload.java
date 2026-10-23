package dev.strategos.mod.server.net.payload;

import dev.strategos.core.protocol.UnitSummary;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.Audience;
import dev.strategos.mod.server.net.ByteBufWire;
import dev.strategos.mod.server.net.PayloadSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * {@code strategos:unit_summary}, server to client (NET-08): every unit a faction knows, as a full frame twice a second;
 * to a faction's seated players only.
 */
public record UnitSummaryPayload(UnitSummary summary) implements CustomPacketPayload {
    public static final Type<UnitSummaryPayload> TYPE = new Type<>(Ids.id("unit_summary"));
    public static final PayloadSpec SPEC =
            PayloadSpec.toClient(TYPE.id(), UnitSummary.WIRE.maxSize(), Audience.Kind.FACTION);
    public static final StreamCodec<RegistryFriendlyByteBuf, UnitSummaryPayload> STREAM_CODEC =
            ByteBufWire.codec(UnitSummary.WIRE, UnitSummaryPayload::new, UnitSummaryPayload::summary);

    @Override
    public Type<UnitSummaryPayload> type() {
        return TYPE;
    }
}
