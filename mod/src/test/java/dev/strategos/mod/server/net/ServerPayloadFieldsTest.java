package dev.strategos.mod.server.net;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.MatchControl;
import dev.strategos.core.protocol.WireWriter;
import dev.strategos.mod.server.net.payload.CameraJumpPayload;
import dev.strategos.mod.server.net.payload.MatchControlPayload;
import dev.strategos.mod.server.net.payload.ModeSwitchPayload;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.stream.Stream;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Raw field values exercise the actual C2S decoder, including malformed values constructors must not admit. */
class ServerPayloadFieldsTest {
    private record Frame(
            String name, StreamCodec<RegistryFriendlyByteBuf, ? extends CustomPacketPayload> codec, byte[] bytes) {
        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Frame> malformedFields() {
        var frames = new ArrayList<Frame>();
        double[] invalid = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        float[] invalidAngles = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (int field = 0; field < 3; field++) {
            for (double bad : invalid) {
                double[] xyz = {1, 2, 3};
                xyz[field] = bad;
                frames.add(new Frame(
                        "camera coordinate " + field + " " + bad,
                        CameraJumpPayload.STREAM_CODEC,
                        new WireWriter()
                                .writeDouble(xyz[0])
                                .writeDouble(xyz[1])
                                .writeDouble(xyz[2])
                                .writeFloat(0)
                                .writeFloat(0)
                                .toByteArray()));
            }
        }
        for (int field = 0; field < 2; field++) {
            for (float bad : invalidAngles) {
                frames.add(new Frame(
                        "camera angle " + field + " " + bad,
                        CameraJumpPayload.STREAM_CODEC,
                        new WireWriter()
                                .writeDouble(1)
                                .writeDouble(2)
                                .writeDouble(3)
                                .writeFloat(field == 0 ? bad : 0)
                                .writeFloat(field == 1 ? bad : 0)
                                .toByteArray()));
            }
        }
        for (boolean firstPerson : new boolean[] {false, true}) {
            for (int field = 0; field < 2; field++) {
                for (double bad : invalid) {
                    frames.add(new Frame(
                            "mode " + firstPerson + " coordinate " + field + " " + bad,
                            ModeSwitchPayload.STREAM_CODEC,
                            new WireWriter()
                                    .writeBoolean(firstPerson)
                                    .writeDouble(field == 0 ? bad : 1)
                                    .writeDouble(field == 1 ? bad : 2)
                                    .toByteArray()));
                }
            }
        }
        for (int action : new int[] {MatchControl.PAUSE, MatchControl.RESUME, MatchControl.SPEED}) {
            for (float bad : invalidAngles) {
                frames.add(new Frame(
                        "control " + action + " " + bad,
                        MatchControlPayload.STREAM_CODEC,
                        new WireWriter().writeByte(action).writeFloat(bad).toByteArray()));
            }
        }
        for (float bad : new float[] {0, -1, 2, Float.MAX_VALUE, Float.MIN_VALUE}) {
            frames.add(new Frame(
                    "unsupported speed " + bad,
                    MatchControlPayload.STREAM_CODEC,
                    new WireWriter()
                            .writeByte(MatchControl.SPEED)
                            .writeFloat(bad)
                            .toByteArray()));
        }
        return frames.stream();
    }

    @ParameterizedTest
    @MethodSource("malformedFields")
    void invalidFieldsBecomeConsumedRefusals(Frame frame) {
        var buf = new RegistryFriendlyByteBuf(
                Unpooled.wrappedBuffer(frame.bytes()), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
        try {
            CustomPacketPayload decoded = frame.codec().decode(buf);
            boolean refused = switch (decoded) {
                case CameraJumpPayload p -> p.jump() == null;
                case ModeSwitchPayload p -> p.request() == null;
                case MatchControlPayload p -> p.control() == null;
                default -> throw new IllegalStateException("unexpected fixture payload");
            };
            assertThat(refused).isTrue();
            assertThat(buf.readableBytes()).isZero();
        } finally {
            buf.release();
        }
    }
}
