package dev.strategos.mod.server.net.payload;

import dev.strategos.core.protocol.WireCodec;
import dev.strategos.core.protocol.WireException;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.LobbyAction;
import dev.strategos.mod.server.net.PayloadSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.Nullable;

/** Authenticated lobby intent; malformed bytes become a bounded refusal for the ingress violation policy. */
public record LobbyActionPayload(@Nullable LobbyAction action, String problem) implements CustomPacketPayload {
    public static final Type<LobbyActionPayload> TYPE = new Type<>(Ids.id("lobby_action"));
    public static final PayloadSpec SPEC = PayloadSpec.toServer(TYPE.id(), LobbyAction.WIRE.maxSize());
    public static final StreamCodec<RegistryFriendlyByteBuf, LobbyActionPayload> STREAM_CODEC =
            StreamCodec.of(LobbyActionPayload::write, LobbyActionPayload::read);

    public static LobbyActionPayload of(LobbyAction action) {
        return new LobbyActionPayload(action, "");
    }

    private static void write(RegistryFriendlyByteBuf buf, LobbyActionPayload payload) {
        LobbyAction action = payload.action();
        if (action == null) throw new IllegalStateException("a refused lobby intent is never sent");
        buf.writeBytes(WireCodec.encode(LobbyAction.WIRE, action));
    }

    private static LobbyActionPayload read(RegistryFriendlyByteBuf buf) {
        int count = buf.readableBytes();
        if (count > SPEC.cap()) {
            buf.skipBytes(count);
            return new LobbyActionPayload(null, "invalid_lobby_action");
        }
        byte[] bytes = new byte[count];
        buf.readBytes(bytes);
        try {
            return of(WireCodec.decode(LobbyAction.WIRE, bytes));
        } catch (WireException refused) {
            return new LobbyActionPayload(null, "invalid_lobby_action");
        }
    }

    @Override
    public Type<LobbyActionPayload> type() {
        return TYPE;
    }
}
