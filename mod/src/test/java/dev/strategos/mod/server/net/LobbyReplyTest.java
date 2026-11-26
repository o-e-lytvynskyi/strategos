package dev.strategos.mod.server.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.protocol.WireCodec;
import dev.strategos.core.protocol.WireException;
import org.junit.jupiter.api.Test;

class LobbyReplyTest {
    @Test
    void everyBoundedReplyRoundTripsWithoutRosterOrExceptionDetails() {
        for (LobbyReply reply : LobbyReply.values()) {
            byte[] bytes = WireCodec.encode(LobbyReply.WIRE, reply);
            assertThat(bytes).hasSize(2);
            assertThat(WireCodec.decode(LobbyReply.WIRE, bytes)).isEqualTo(reply);
            assertThat(reply.translationKey()).startsWith("strategos.lobby.reply.");
        }
    }

    @Test
    void unsupportedVersionsCodesTruncationAndTrailingBytesAreWireFaults() {
        for (byte[] bytes : new byte[][] {{}, {1}, {2, 0}, {1, 127}, {1, 0, 0}}) {
            assertThatThrownBy(() -> WireCodec.decode(LobbyReply.WIRE, bytes)).isInstanceOf(WireException.class);
        }
    }
}
