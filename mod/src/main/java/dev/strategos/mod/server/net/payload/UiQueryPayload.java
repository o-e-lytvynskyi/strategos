package dev.strategos.mod.server.net.payload;

import dev.strategos.core.protocol.UiQuery;
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

/** {@code strategos:ui_query}, client to server (NET-08): the own units whose details a player wants, at most 32, at most four queries a second. */
public record UiQueryPayload(long epoch, @Nullable UiQuery query) implements MatchScopedPayload {
    public UiQueryPayload(@Nullable UiQuery query) {
        this(0, query);
    }

    private static final WireCodec<SessionEnvelope<UiQuery>> WIRE = SessionEnvelope.codec(UiQuery.WIRE);
    public static final Type<UiQueryPayload> TYPE = new Type<>(Ids.id("ui_query"));
    public static final PayloadSpec SPEC = PayloadSpec.toServer(TYPE.id(), WIRE.maxSize());
    public static final StreamCodec<RegistryFriendlyByteBuf, UiQueryPayload> STREAM_CODEC = ByteBufWire.serverCodec(
            WIRE,
            frame -> new UiQueryPayload(frame.epoch(), frame.body()),
            p -> p.query == null ? null : new SessionEnvelope<>(p.epoch, p.query),
            () -> new UiQueryPayload(0, null));

    @Override
    public boolean decoded() {
        return query != null;
    }

    @Override
    public UiQueryPayload withEpoch(long epoch) {
        return new UiQueryPayload(epoch, query);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
