package dev.strategos.mod.server.net;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.CommandBatch;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Feedback;
import dev.strategos.core.protocol.Limits;
import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.core.protocol.MatchStateMsg;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.NoticeKind;
import dev.strategos.core.protocol.NoticeMsg;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.protocol.WireWriter;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.mod.server.net.payload.CommandsPayload;
import dev.strategos.mod.server.net.payload.FeedbackPayload;
import dev.strategos.mod.server.net.payload.MatchJoinPayload;
import dev.strategos.mod.server.net.payload.MatchStatePayload;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

/**
 * NET-01.1 (spec §7.3): every payload round-trips through its stream codec with no byte left and stays within its
 * type's cap; a batch the server cannot read arrives as a refusal, with every byte consumed, never as an error.
 */
class PayloadRoundTripTest {
    @Test
    void everyPayloadRoundTripsWithinItsCap() {
        Move move = Move.replace(UnitSet.of(5, 9), new Vec2d(10.5, 20.5));
        roundTrip(
                CommandsPayload.STREAM_CODEC,
                CommandsPayload.of(new CommandBatch(3, List.of(move))),
                Limits.BATCH_BYTES);
        roundTrip(
                MatchJoinPayload.STREAM_CODEC,
                new MatchJoinPayload(
                        new MatchJoin(1, 100, -60, -200, 48, 48, -64, 319, 2, new PlayerSlot(0), "{\"a\":1}")),
                MatchJoinPayload.SPEC.cap());
        roundTrip(
                MatchStatePayload.STREAM_CODEC,
                new MatchStatePayload(new MatchStateMsg(1, MatchStateMsg.Lifecycle.PAUSED, 77)),
                MatchStatePayload.SPEC.cap());
        roundTrip(
                FeedbackPayload.STREAM_CODEC,
                new FeedbackPayload(new Feedback(9, CommandOutcome.PARTIAL, RejectReason.NOT_OWNER, 1, 2)),
                FeedbackPayload.SPEC.cap());
        roundTrip(
                FeedbackPayload.STREAM_CODEC,
                new FeedbackPayload(new NoticeMsg(NoticeKind.STUCK, 12345, "boxed in")),
                FeedbackPayload.SPEC.cap());
        assertThat(MatchStatePayload.SPEC.cap()).isLessThanOrEqualTo(256);
        assertThat(FeedbackPayload.SPEC.cap()).isLessThanOrEqualTo(1024);
        assertThat(MatchJoinPayload.SPEC.cap()).isLessThanOrEqualTo(4096);
    }

    @Test
    void anUnreadableBatchArrivesAsARefusal() {
        byte[] thirtyThree = new WireWriter().writeVarInt(0).writeVarInt(33).toByteArray();
        CommandsPayload malformed = read(thirtyThree);
        assertThat(malformed.ok()).isFalse();
        assertThat(malformed.problem()).isNotEmpty();

        CommandsPayload oversized = read(new byte[Limits.BATCH_BYTES + 100]);
        assertThat(oversized.ok()).isFalse();
        assertThat(oversized.problem()).contains("above the batch's 4096");
    }

    private static CommandsPayload read(byte[] bytes) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(
                Unpooled.wrappedBuffer(bytes), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
        try {
            CommandsPayload payload = CommandsPayload.STREAM_CODEC.decode(buf);
            assertThat(buf.readableBytes()).as("bytes left unread").isZero();
            return payload;
        } finally {
            buf.release();
        }
    }

    private static <P> void roundTrip(StreamCodec<RegistryFriendlyByteBuf, P> codec, P payload, int cap) {
        RegistryFriendlyByteBuf buf =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
        try {
            codec.encode(buf, payload);
            assertThat(buf.readableBytes()).as("%s within its cap", payload).isLessThanOrEqualTo(cap);
            assertThat(codec.decode(buf)).isEqualTo(payload);
            assertThat(buf.readableBytes()).as("bytes left after %s", payload).isZero();
        } finally {
            buf.release();
        }
    }
}
