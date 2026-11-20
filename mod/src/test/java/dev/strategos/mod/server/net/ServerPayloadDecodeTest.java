package dev.strategos.mod.server.net;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.CameraJump;
import dev.strategos.core.protocol.CommandBatch;
import dev.strategos.core.protocol.DebugSubscribe;
import dev.strategos.core.protocol.MatchControl;
import dev.strategos.core.protocol.ModeSwitch;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SkirmishSetup;
import dev.strategos.core.protocol.TemplateAck;
import dev.strategos.core.protocol.UiQuery;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.mod.registry.ModPayloads;
import dev.strategos.mod.server.net.payload.CameraJumpPayload;
import dev.strategos.mod.server.net.payload.CommandsPayload;
import dev.strategos.mod.server.net.payload.DebugSubscribePayload;
import dev.strategos.mod.server.net.payload.LobbyActionPayload;
import dev.strategos.mod.server.net.payload.MatchControlPayload;
import dev.strategos.mod.server.net.payload.ModeSwitchPayload;
import dev.strategos.mod.server.net.payload.SkirmishPayload;
import dev.strategos.mod.server.net.payload.TemplateAckPayload;
import dev.strategos.mod.server.net.payload.UiQueryPayload;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Every registered C2S decoder returns a bounded refusal; valid traffic must survive the same entry. */
class ServerPayloadDecodeTest {
    private record Input<P extends CustomPacketPayload>(
            PayloadSpec spec, StreamCodec<RegistryFriendlyByteBuf, P> codec, P valid, Predicate<P> refused) {
        @Override
        public String toString() {
            return spec.id().toString();
        }
    }

    static Stream<Input<?>> inputs() {
        var config = new LobbyConfiguration(
                1,
                List.of(new LobbyConfiguration.Seat(
                        0, LobbyConfiguration.Kind.HUMAN, 0, 0, "strategos:placeholder", 0)),
                SkirmishSetup.DEFAULTS);
        return Stream.of(
                new Input<>(
                        CommandsPayload.SPEC,
                        CommandsPayload.STREAM_CODEC,
                        CommandsPayload.of(new CommandBatch(1, List.of(Move.replace(UnitSet.of(1), new Vec2d(4, 4))))),
                        p -> p.batch() == null),
                new Input<>(
                        LobbyActionPayload.SPEC,
                        LobbyActionPayload.STREAM_CODEC,
                        LobbyActionPayload.of(new LobbyAction.Open(config)),
                        p -> p.action() == null),
                new Input<>(
                        CameraJumpPayload.SPEC,
                        CameraJumpPayload.STREAM_CODEC,
                        new CameraJumpPayload(new CameraJump(1, 2, 3, 0, 0)),
                        p -> p.jump() == null),
                new Input<>(
                        ModeSwitchPayload.SPEC,
                        ModeSwitchPayload.STREAM_CODEC,
                        new ModeSwitchPayload(new ModeSwitch(false, 1, 2)),
                        p -> p.request() == null),
                new Input<>(
                        MatchControlPayload.SPEC,
                        MatchControlPayload.STREAM_CODEC,
                        new MatchControlPayload(MatchControl.PAUSE_NOW),
                        p -> p.control() == null),
                new Input<>(
                        SkirmishPayload.SPEC,
                        SkirmishPayload.STREAM_CODEC,
                        new SkirmishPayload(SkirmishSetup.DEFAULTS),
                        p -> p.setup() == null),
                new Input<>(
                        TemplateAckPayload.SPEC,
                        TemplateAckPayload.STREAM_CODEC,
                        new TemplateAckPayload(new TemplateAck(new byte[TemplateAck.SHA_BYTES])),
                        p -> p.ack() == null),
                new Input<>(
                        UiQueryPayload.SPEC,
                        UiQueryPayload.STREAM_CODEC,
                        new UiQueryPayload(new UiQuery(List.of(1))),
                        p -> p.query() == null),
                new Input<>(
                        DebugSubscribePayload.SPEC,
                        DebugSubscribePayload.STREAM_CODEC,
                        new DebugSubscribePayload(DebugSubscribe.NONE),
                        p -> p.request() == null));
    }

    @Test
    void theFixturesCoverEveryRegisteredServerboundType() {
        assertThat(inputs().map(input -> input.spec().id()).collect(Collectors.toSet()))
                .isEqualTo(ModPayloads.specs().stream()
                        .filter(spec -> !spec.toClient())
                        .map(PayloadSpec::id)
                        .collect(Collectors.toSet()));
    }

    @ParameterizedTest
    @MethodSource("inputs")
    <P extends CustomPacketPayload> void emptyInputIsConsumedAndRefused(Input<P> input) {
        refused(input, new byte[0]);
    }

    @ParameterizedTest
    @MethodSource("inputs")
    <P extends CustomPacketPayload> void overCapInputIsConsumedAndRefused(Input<P> input) {
        refused(input, new byte[input.spec().cap() + 1]);
    }

    @ParameterizedTest
    @MethodSource("inputs")
    <P extends CustomPacketPayload> void truncatedValidInputIsConsumedAndRefused(Input<P> input) {
        byte[] valid = encoded(input, input.valid());
        refused(input, Arrays.copyOf(valid, valid.length - 1));
    }

    @ParameterizedTest
    @MethodSource("inputs")
    <P extends CustomPacketPayload> void trailingBytesAreConsumedAndRefused(Input<P> input) {
        byte[] valid = encoded(input, input.valid());
        refused(input, Arrays.copyOf(valid, valid.length + 1));
    }

    @ParameterizedTest
    @MethodSource("inputs")
    <P extends CustomPacketPayload> void validTrafficRemainsByteExact(Input<P> input) {
        byte[] valid = encoded(input, input.valid());
        var buf = buffer(valid);
        try {
            P decoded = input.codec().decode(buf);
            assertThat(input.refused().test(decoded)).isFalse();
            assertThat(buf.readableBytes()).isZero();
            assertThat(encoded(input, decoded)).containsExactly(valid);
        } finally {
            buf.release();
        }
    }

    private static <P extends CustomPacketPayload> void refused(Input<P> input, byte[] bytes) {
        var buf = buffer(bytes);
        try {
            P decoded = input.codec().decode(buf);
            assertThat(input.refused().test(decoded)).isTrue();
            assertThat(buf.readableBytes()).isZero();
        } finally {
            buf.release();
        }
    }

    private static <P extends CustomPacketPayload> byte[] encoded(Input<P> input, P value) {
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
        try {
            input.codec().encode(buf, value);
            assertThat(buf.readableBytes()).isLessThanOrEqualTo(input.spec().cap());
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return bytes;
        } finally {
            buf.release();
        }
    }

    private static RegistryFriendlyByteBuf buffer(byte[] bytes) {
        return new RegistryFriendlyByteBuf(
                Unpooled.wrappedBuffer(bytes), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
    }
}
