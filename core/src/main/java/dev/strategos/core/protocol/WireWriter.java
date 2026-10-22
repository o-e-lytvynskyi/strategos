package dev.strategos.core.protocol;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Writes the wire format into a growable array: unsigned and zigzag varints, big-endian fixed-size numbers, doubles as
 * their raw bits, length-prefixed bytes and UTF-8 strings. {@link WireReader} reads exactly what this writes.
 */
public final class WireWriter {
    private byte[] buf;
    private int size;

    public WireWriter() {
        this(64);
    }

    public WireWriter(int initialCapacity) {
        buf = new byte[Math.max(16, initialCapacity)];
    }

    public int size() {
        return size;
    }

    public byte[] toByteArray() {
        return Arrays.copyOf(buf, size);
    }

    public WireWriter writeByte(int value) {
        ensure(1);
        buf[size++] = (byte) value;
        return this;
    }

    public WireWriter writeBoolean(boolean value) {
        return writeByte(value ? 1 : 0);
    }

    public WireWriter writeShort(int value) {
        ensure(2);
        buf[size++] = (byte) (value >>> 8);
        buf[size++] = (byte) value;
        return this;
    }

    public WireWriter writeInt(int value) {
        ensure(4);
        for (int shift = 24; shift >= 0; shift -= 8) {
            buf[size++] = (byte) (value >>> shift);
        }
        return this;
    }

    public WireWriter writeLong(long value) {
        ensure(8);
        for (int shift = 56; shift >= 0; shift -= 8) {
            buf[size++] = (byte) (value >>> shift);
        }
        return this;
    }

    public WireWriter writeFloat(float value) {
        return writeInt(Float.floatToRawIntBits(value));
    }

    public WireWriter writeDouble(double value) {
        return writeLong(Double.doubleToRawLongBits(value));
    }

    /** An unsigned varint: 1 to 5 bytes; a negative value is an error, use {@link #writeZigZag(int)}. */
    public WireWriter writeVarInt(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("varint of a negative value " + value + "; use zigzag");
        }
        return writeVarLong(value);
    }

    /** An unsigned varlong: 1 to 10 bytes; a negative value is an error, use {@link #writeZigZag(long)}. */
    public WireWriter writeVarLong(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("varlong of a negative value " + value + "; use zigzag");
        }
        return writeUnsigned(value);
    }

    /** A signed int as a zigzag varint: small magnitudes of either sign take few bytes. */
    public WireWriter writeZigZag(int value) {
        return writeUnsigned(Integer.toUnsignedLong((value << 1) ^ (value >> 31)));
    }

    /** A signed long as a zigzag varlong. */
    public WireWriter writeZigZag(long value) {
        return writeUnsigned((value << 1) ^ (value >> 63));
    }

    /** A varint length, then the bytes. */
    public WireWriter writeBytes(byte[] bytes) {
        writeVarInt(bytes.length);
        ensure(bytes.length);
        System.arraycopy(bytes, 0, buf, size, bytes.length);
        size += bytes.length;
        return this;
    }

    /** A varint byte length, then UTF-8; longer than {@code maxBytes} is an error, as reading it would be. */
    public WireWriter writeString(String value, int maxBytes) {
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        if (utf8.length > maxBytes) {
            throw new IllegalArgumentException(
                    "string of " + utf8.length + " bytes above its bound " + maxBytes + ": " + value);
        }
        return writeBytes(utf8);
    }

    /** Raw bytes with no length. */
    public WireWriter writeRaw(byte[] bytes) {
        ensure(bytes.length);
        System.arraycopy(bytes, 0, buf, size, bytes.length);
        size += bytes.length;
        return this;
    }

    /** The 64 bits of {@code value} as an unsigned varint. */
    private WireWriter writeUnsigned(long value) {
        ensure(10);
        long v = value;
        while ((v & ~0x7FL) != 0) {
            buf[size++] = (byte) ((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        buf[size++] = (byte) v;
        return this;
    }

    private void ensure(int more) {
        if (size + more > buf.length) {
            buf = Arrays.copyOf(buf, Math.max(buf.length * 2, size + more));
        }
    }
}
