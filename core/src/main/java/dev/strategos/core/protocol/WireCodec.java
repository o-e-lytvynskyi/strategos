package dev.strategos.core.protocol;

/**
 * The binary form of one record type, for the wire and the journal: written by {@link WireWriter}, read back by
 * {@link WireReader}, and never longer than {@link #maxSize()}, which decoding enforces before it reads a byte.
 */
public interface WireCodec<T> {
    void write(WireWriter out, T value);

    /** Reads one value; anything malformed throws {@link WireException}. */
    T read(WireReader in);

    /** An upper bound on the encoded size of any value of this type. */
    int maxSize();

    /** The value's bytes; a value above {@link #maxSize()} is a bug in the codec and throws. */
    static <T> byte[] encode(WireCodec<T> codec, T value) {
        WireWriter out = new WireWriter(Math.min(codec.maxSize(), 1 << 16));
        codec.write(out, value);
        if (out.size() > codec.maxSize()) {
            throw new IllegalStateException(
                    "encoded " + out.size() + " bytes above the codec's bound " + codec.maxSize());
        }
        return out.toByteArray();
    }

    /** Exactly one value, with no trailing byte, from at most {@link #maxSize()} bytes. */
    static <T> T decode(WireCodec<T> codec, byte[] bytes) {
        if (bytes.length > codec.maxSize()) {
            throw new WireException(bytes.length + " bytes above the bound " + codec.maxSize());
        }
        WireReader in = new WireReader(bytes);
        T value = codec.read(in);
        in.expectEnd();
        return value;
    }
}
