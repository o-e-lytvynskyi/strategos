package dev.strategos.mod.server.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.protocol.WireCodec;
import dev.strategos.core.protocol.WireException;
import dev.strategos.core.protocol.WireReader;
import dev.strategos.core.protocol.WireWriter;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SessionEnvelopeTest {
    private static final class Body implements WireCodec<Integer> {
        int reads;
        private final int bound;

        Body(int bound) {
            this.bound = bound;
        }

        @Override
        public void write(WireWriter out, Integer value) {
            out.writeInt(value);
        }

        @Override
        public Integer read(WireReader in) {
            reads++;
            return in.readInt();
        }

        @Override
        public int maxSize() {
            return bound;
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, Long.MIN_VALUE, Long.MAX_VALUE})
    void theCodecPreservesUntrustedEpochBitsWithoutGrantingAuthority(long epoch) {
        var body = new Body(4);
        var wire = SessionEnvelope.codec(body);
        var frame = new SessionEnvelope<>(epoch, 37);
        var encoded = WireCodec.encode(wire, frame);
        assertThat(encoded).hasSize(12);
        assertThat(WireCodec.decode(wire, encoded)).isEqualTo(frame);
        assertThat(body.reads).isEqualTo(1);
    }

    @Test
    void theHeaderIsExactlyEightBytesAndTheBodyHasItsOriginalLayout() {
        var wire = SessionEnvelope.codec(new Body(4));
        var encoded = WireCodec.encode(wire, new SessionEnvelope<>(0x0102030405060708L, 0x10203040));
        assertThat(encoded)
                .containsExactly(
                        (byte) 1, (byte) 2, (byte) 3, (byte) 4, (byte) 5, (byte) 6, (byte) 7, (byte) 8, (byte) 16,
                        (byte) 32, (byte) 48, (byte) 64);
        assertThat(wire.maxSize()).isEqualTo(8 + 4);
    }

    @Test
    void everyTruncatedHeaderOrBodyFailsAndAnIncompleteHeaderNeverInvokesTheBodyDecoder() {
        var full = WireCodec.encode(SessionEnvelope.codec(new Body(4)), new SessionEnvelope<>(7L, 42));
        for (int length = 0; length < full.length; length++) {
            var body = new Body(4);
            var wire = SessionEnvelope.codec(body);
            var truncated = Arrays.copyOf(full, length);
            assertThatThrownBy(() -> WireCodec.decode(wire, truncated)).isInstanceOf(WireException.class);
            assertThat(body.reads).isEqualTo(length < 8 ? 0 : 1);
        }
    }

    @Test
    void anOversizedFrameIsRejectedBeforeAnyBodyRead() {
        var body = new Body(4);
        var wire = SessionEnvelope.codec(body);
        for (int size : new int[] {13, 10000})
            assertThatThrownBy(() -> WireCodec.decode(wire, new byte[size])).isInstanceOf(WireException.class);
        assertThat(body.reads).isZero();
    }

    @Test
    void addingAHeaderCannotOverflowTheEncodedFrameLimit() {
        assertThatThrownBy(() -> SessionEnvelope.codec(new Body(Integer.MAX_VALUE)))
                .isInstanceOf(ArithmeticException.class);
    }
}
