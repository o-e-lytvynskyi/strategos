package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;
import java.util.Arrays;

/**
 * A client's answer to every {@code template_set} (DATA-03.6, spec §1.6 D row 9): the SHA-256 of the set it decoded, so
 * the server resends when it differs from the version it sent.
 */
@CoreApi
public final class TemplateAck {
    public static final int SHA_BYTES = 32;

    private final byte[] sha;

    public TemplateAck(byte[] sha) {
        if (sha.length != SHA_BYTES) {
            throw new IllegalArgumentException("a template ack carries " + SHA_BYTES + " bytes, not " + sha.length);
        }
        this.sha = sha.clone();
    }

    public byte[] sha() {
        return sha.clone();
    }

    /** Whether it acknowledges the set whose version is {@code version}, the SHA-256 in hex. */
    public boolean matches(String version) {
        return HexFormatter.hex(sha).equals(version);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TemplateAck other && Arrays.equals(sha, other.sha);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(sha);
    }

    @Override
    public String toString() {
        return "TemplateAck[" + HexFormatter.hex(sha) + "]";
    }

    public static final WireCodec<TemplateAck> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, TemplateAck ack) {
            out.writeRaw(ack.sha);
        }

        @Override
        public TemplateAck read(WireReader in) {
            return new TemplateAck(in.readRaw(SHA_BYTES));
        }

        @Override
        public int maxSize() {
            return SHA_BYTES;
        }
    };

    /** Lower-case hex, as {@code TemplateSet#version} writes it. */
    static final class HexFormatter {
        private HexFormatter() {}

        static String hex(byte[] bytes) {
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        }
    }
}
