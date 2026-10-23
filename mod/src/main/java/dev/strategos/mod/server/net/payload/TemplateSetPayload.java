package dev.strategos.mod.server.net.payload;

import dev.strategos.core.protocol.TemplateSetView;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.Audience;
import dev.strategos.mod.server.net.ByteBufWire;
import dev.strategos.mod.server.net.PayloadSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * {@code strategos:template_set}, server to one player (DATA-03): the server's template set, at login before the player
 * enters the level and after every reload, so the client knows each unit's body before it sees one.
 */
public record TemplateSetPayload(TemplateSetView view) implements CustomPacketPayload {
    public static final Type<TemplateSetPayload> TYPE = new Type<>(Ids.id("template_set"));
    public static final PayloadSpec SPEC =
            PayloadSpec.toClient(TYPE.id(), TemplateSetView.WIRE.maxSize(), Audience.Kind.PLAYER);
    public static final StreamCodec<RegistryFriendlyByteBuf, TemplateSetPayload> STREAM_CODEC =
            ByteBufWire.codec(TemplateSetView.WIRE, TemplateSetPayload::new, TemplateSetPayload::view);

    @Override
    public Type<TemplateSetPayload> type() {
        return TYPE;
    }
}
