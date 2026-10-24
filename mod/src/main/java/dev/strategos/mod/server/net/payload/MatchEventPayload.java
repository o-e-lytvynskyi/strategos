package dev.strategos.mod.server.net.payload;

import dev.strategos.core.protocol.MatchNews;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.Audience;
import dev.strategos.mod.server.net.ByteBufWire;
import dev.strategos.mod.server.net.PayloadSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * {@code strategos:match_event}, server to client (MATCH-01): a faction defeated, or the match's end with its winner and
 * duration, to every participant of the match; an under-attack alert to the attacked faction (UI-12).
 */
public record MatchEventPayload(MatchNews news) implements CustomPacketPayload {
    public static final Type<MatchEventPayload> TYPE = new Type<>(Ids.id("match_event"));
    public static final PayloadSpec SPEC =
            PayloadSpec.toClient(TYPE.id(), MatchNews.WIRE.maxSize(), Audience.Kind.MATCH, Audience.Kind.FACTION);
    public static final StreamCodec<RegistryFriendlyByteBuf, MatchEventPayload> STREAM_CODEC =
            ByteBufWire.codec(MatchNews.WIRE, MatchEventPayload::new, MatchEventPayload::news);

    @Override
    public Type<MatchEventPayload> type() {
        return TYPE;
    }
}
