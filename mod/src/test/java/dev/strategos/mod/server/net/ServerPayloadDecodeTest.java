package dev.strategos.mod.server.net;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.gametest.net.ServerboundCases;
import dev.strategos.gametest.net.ServerboundCases.Input;
import dev.strategos.mod.registry.ModPayloads;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Every registered C2S decoder returns a bounded refusal; valid traffic must survive the same entry. */
class ServerPayloadDecodeTest {
    static Stream<Input<?>> inputs() {
        return ServerboundCases.inputs();
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
