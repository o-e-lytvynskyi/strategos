package dev.strategos.core.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Reads what {@link WireWriter} writes, never past its window: every overrun, every count or length above the bound the
 * caller passes, every malformed varint or UTF-8 string throws {@link WireException}, and nothing is allocated beyond a
 * bound. {@link #expectEnd()} rejects trailing bytes.
 */
public final class WireReader {
    private final byte[] buf;
    private final int end;
    private int pos;

    public WireReader(byte[] buf) {
        this(buf, 0, buf.length);
    }

    public WireReader(byte[] buf, int offset, int length) {
        if (offset < 0 || length < 0 || offset + length > buf.length) {
            throw new IllegalArgumentException("window " + offset + "+" + length + " outside " + buf.length + " bytes");
        }
        this.buf = buf;
        this.pos = offset;
        this.end = offset + length;
    }

    public int remaining() {
        return end - pos;
    }

    public int readByte() {
        need(1);
        return buf[pos++];
    }

    public int readUnsignedByte() {
        return readByte() & 0xFF;
    }

    public boolean readBoolean() {
        int b = readUnsignedByte();
        if (b > 1) {
            throw new WireException("boolean byte " + b);
        }
        return b == 1;
    }

    public int readUnsignedShort() {
        need(2);
        int v = ((buf[pos] & 0xFF) << 8) | (buf[pos + 1] & 0xFF);
        pos += 2;
        return v;
    }

    public int readInt() {
        need(4);
        int v = 0;
        for (int i = 0; i < 4; i++) {
            v = (v << 8) | (buf[pos++] & 0xFF);
        }
        return v;
    }

    public long readLong() {
        need(8);
        long v = 0;
        for (int i = 0; i < 8; i++) {
            v = (v << 8) | (buf[pos++] & 0xFF);
        }
        return v;
    }

    public float readFloat() {
        return Float.intBitsToFloat(readInt());
    }

    public double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    /** An unsigned varint of at most 5 bytes that fits a non-negative {@code int}. */
    public int readVarInt() {
        long v = readVarLong(5);
        if (v > Integer.MAX_VALUE) {
            throw new WireException("varint " + v + " above " + Integer.MAX_VALUE);
        }
        return (int) v;
    }

    /** An unsigned varint no larger than {@code max}. */
    public int readVarInt(int max) {
        int v = readVarInt();
        if (v > max) {
            throw new WireException("value " + v + " above its bound " + max);
        }
        return v;
    }

    /** An unsigned varlong of at most 10 bytes that fits a non-negative {@code long}. */
    public long readVarLong() {
        long v = readVarLong(10);
        if (v < 0) {
            throw new WireException("varlong above " + Long.MAX_VALUE);
        }
        return v;
    }

    public int readZigZagInt() {
        long zig = readVarLong(5);
        if (zig > 0xFFFF_FFFFL) {
            throw new WireException("zigzag int out of range");
        }
        int z = (int) zig;
        return (z >>> 1) ^ -(z & 1);
    }

    public long readZigZagLong() {
        long zig = readVarLong(10);
        return (zig >>> 1) ^ -(zig & 1);
    }

    /** A length-prefixed byte array of at most {@code maxBytes}. */
    public byte[] readBytes(int maxBytes) {
        int length = readVarInt(maxBytes);
        need(length);
        byte[] out = Arrays.copyOfRange(buf, pos, pos + length);
        pos += length;
        return out;
    }

    /** A length-prefixed UTF-8 string of at most {@code maxBytes} bytes; malformed UTF-8 throws. */
    public String readString(int maxBytes) {
        int length = readVarInt(maxBytes);
        need(length);
        try {
            String s = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(buf, pos, length))
                    .toString();
            pos += length;
            return s;
        } catch (CharacterCodingException e) {
            throw new WireException("malformed UTF-8 in a string of " + length + " bytes");
        }
    }

    /** Exactly {@code length} raw bytes. */
    public byte[] readRaw(int length) {
        need(length);
        byte[] out = Arrays.copyOfRange(buf, pos, pos + length);
        pos += length;
        return out;
    }

    /** Throws if any byte is left in the window. */
    public void expectEnd() {
        if (pos != end) {
            throw new WireException((end - pos) + " trailing bytes");
        }
    }

    private long readVarLong(int maxBytes) {
        long v = 0;
        for (int i = 0; i < maxBytes; i++) {
            int b = readUnsignedByte();
            if (i == 9 && (b & 0x7E) != 0) {
                throw new WireException("varint above 64 bits");
            }
            v |= (long) (b & 0x7F) << (7 * i);
            if ((b & 0x80) == 0) {
                if (i > 0 && b == 0) {
                    throw new WireException("varint with a redundant zero byte");
                }
                return v;
            }
        }
        throw new WireException("varint longer than " + maxBytes + " bytes");
    }

    private void need(int n) {
        if (n < 0 || end - pos < n) {
            throw new WireException("needs " + n + " bytes, " + (end - pos) + " left");
        }
    }
}
