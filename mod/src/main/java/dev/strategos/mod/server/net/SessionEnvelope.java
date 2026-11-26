package dev.strategos.mod.server.net;

import dev.strategos.core.protocol.WireCodec;
import dev.strategos.core.protocol.WireReader;
import dev.strategos.core.protocol.WireWriter;
import java.util.Objects;

/** Transport data only: the server must authenticate the epoch against its current recipient view. */
public record SessionEnvelope<T>(long epoch, T body) {
    public SessionEnvelope {
        Objects.requireNonNull(body, "body");
    }

    /** The fixed header consumes its own exact bytes; the body's published bound is unchanged. */
    public static <T> WireCodec<SessionEnvelope<T>> codec(WireCodec<T> body) {
        int bound = Math.addExact(Long.BYTES, body.maxSize());
        return new WireCodec<>() {
            @Override
            public void write(WireWriter out, SessionEnvelope<T> frame) {
                out.writeLong(frame.epoch());
                body.write(out, frame.body());
            }

            @Override
            public SessionEnvelope<T> read(WireReader in) {
                long epoch = in.readLong();
                return new SessionEnvelope<>(epoch, body.read(in));
            }

            @Override
            public int maxSize() {
                return bound;
            }
        };
    }
}
