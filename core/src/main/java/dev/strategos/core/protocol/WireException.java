package dev.strategos.core.protocol;

/** Bytes that do not decode: an overrun, a count or length above its bound, an unknown id, or trailing bytes. */
public final class WireException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public WireException(String message) {
        super(message);
    }
}
