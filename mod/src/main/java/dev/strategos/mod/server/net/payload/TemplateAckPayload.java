package dev.strategos.mod.server.net.payload;

import dev.strategos.core.protocol.TemplateAck;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.ByteBufWire;
import dev.strategos.mod.server.net.PayloadSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * {@code strategos:template_ack}, client to server (DATA-03.6, spec §1.6 D row 9): the SHA-256 of the template set the
 * client decoded; the server resends the set when it is not the one it sent.
 */
public record TemplateAckPayload(TemplateAck ack) implements CustomPacketPayload {
    public static final Type<TemplateAckPayload> TYPE = new Type<>(Ids.id("template_ack"));
    public static final PayloadSpec SPEC = PayloadSpec.toServer(TYPE.id(), TemplateAck.WIRE.maxSize());
    public static final StreamCodec<RegistryFriendlyByteBuf, TemplateAckPayload> STREAM_CODEC =
            ByteBufWire.codec(TemplateAck.WIRE, TemplateAckPayload::new, TemplateAckPayload::ack);

    @Override
    public Type<TemplateAckPayload> type() {
        return TYPE;
    }
}
