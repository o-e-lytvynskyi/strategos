package dev.strategos.mod.server.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.protocol.SkirmishSetup;
import dev.strategos.mod.registry.ModPayloads;
import dev.strategos.mod.server.net.payload.LobbyActionPayload;
import dev.strategos.mod.server.net.payload.LobbyReplyPayload;
import dev.strategos.mod.server.net.payload.LobbyStatePayload;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class LobbyPayloadTest {
    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
    }

    @Test
    void realIntentAndStateStreamCodecsConsumeExactlyTheirBoundedMessages() {
        var config = new LobbyConfiguration(
                1,
                List.of(new LobbyConfiguration.Seat(
                        0, LobbyConfiguration.Kind.HUMAN, 0, 0, "strategos:placeholder", 0)),
                SkirmishSetup.DEFAULTS);
        var action = LobbyActionPayload.of(new LobbyAction.Open(config));
        var state = new LobbyStatePayload(new LobbyState(
                config,
                List.of(new LobbyState.Member(0, "host", false, true)),
                0,
                true,
                0,
                0,
                LobbyState.Phase.EDITING,
                ""));
        var buf = buffer();
        try {
            LobbyActionPayload.STREAM_CODEC.encode(buf, action);
            assertThat(buf.readableBytes()).isLessThanOrEqualTo(LobbyActionPayload.SPEC.cap());
            assertThat(LobbyActionPayload.STREAM_CODEC.decode(buf)).isEqualTo(action);
            assertThat(buf.readableBytes()).isZero();
            LobbyStatePayload.STREAM_CODEC.encode(buf, state);
            assertThat(buf.readableBytes()).isLessThanOrEqualTo(LobbyStatePayload.SPEC.cap());
            assertThat(LobbyStatePayload.STREAM_CODEC.decode(buf)).isEqualTo(state);
            assertThat(buf.readableBytes()).isZero();
        } finally {
            buf.release();
        }
    }

    @Test
    void malformedOrOversizedIntentsAreDataRefusalsAndConsumeEveryByte() {
        for (byte[] bytes : List.of(
                new byte[0], new byte[] {127, 0}, new byte[] {1, 127}, new byte[LobbyActionPayload.SPEC.cap() + 1])) {
            var buf = buffer();
            try {
                buf.writeBytes(bytes);
                var refused = LobbyActionPayload.STREAM_CODEC.decode(buf);
                assertThat(refused.action()).isNull();
                assertThat(refused.problem()).isEqualTo("invalid_lobby_action");
                assertThat(buf.readableBytes()).isZero();
                assertThatThrownBy(() -> LobbyActionPayload.STREAM_CODEC.encode(buf, refused))
                        .isInstanceOf(IllegalStateException.class);
            } finally {
                buf.release();
            }
        }
    }

    @Test
    void refusalRepliesHaveOnlyAPrivatePlayerAudienceAndNoRoster() {
        assertThat(LobbyReplyPayload.SPEC.audiences()).containsExactly(Audience.Kind.PLAYER);
        assertThat(ModPayloads.spec(LobbyReplyPayload.TYPE.id())).isEqualTo(LobbyReplyPayload.SPEC);
        for (LobbyReply reply : LobbyReply.values()) {
            var payload = new LobbyReplyPayload(reply);
            var buf = buffer();
            try {
                LobbyReplyPayload.STREAM_CODEC.encode(buf, payload);
                assertThat(buf.readableBytes()).isEqualTo(2);
                assertThat(LobbyReplyPayload.STREAM_CODEC.decode(buf)).isEqualTo(payload);
                assertThat(buf.readableBytes()).isZero();
            } finally {
                buf.release();
            }
        }
    }

    @Test
    void onlyOnePlayerMayReceivePrivateLobbyStateAndIntentIsNeverAServerSend() {
        assertThat(ModPayloads.spec(LobbyStatePayload.TYPE.id())).isEqualTo(LobbyStatePayload.SPEC);
        assertThat(ModPayloads.spec(LobbyActionPayload.TYPE.id())).isEqualTo(LobbyActionPayload.SPEC);
        assertThat(LobbyStatePayload.SPEC.audiences()).containsExactly(Audience.Kind.PLAYER);
        for (Audience.Kind audience : Audience.Kind.values()) {
            if (audience != Audience.Kind.PLAYER)
                assertThatThrownBy(
                                () -> NetGateway.check(LobbyStatePayload.SPEC, LobbyStatePayload.TYPE.id(), audience))
                        .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> NetGateway.check(LobbyActionPayload.SPEC, LobbyActionPayload.TYPE.id(), audience))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
