package dev.strategos.core.shared;

/**
 * An order-sensitive 64-bit hash for state hashes; equal sequences of puts give equal hashes on every platform. The
 * static {@link #start}, {@link #mix} and {@link #finish} are the same function on a plain {@code long}, for hot paths
 * that must not allocate; {@link #resume} hands such a state to code that takes a {@code Hasher}.
 */
public final class Hasher {
    private long h;

    public Hasher(long seed) {
        h = start(seed);
    }

    private Hasher() {}

    public static long start(long seed) {
        return Rng.mix64(seed ^ 0x5354524154454753L);
    }

    /** {@code putLong} on a plain state. */
    public static long mix(long state, long v) {
        return Rng.mix64(state ^ Rng.mix64(v + Rng.GOLDEN_GAMMA));
    }

    public static long finish(long state) {
        return Rng.mix64(state);
    }

    /** A hasher that continues from {@code state}. */
    public static Hasher resume(long state) {
        Hasher hasher = new Hasher();
        hasher.h = state;
        return hasher;
    }

    /** The running state, before {@link #finish()}. */
    public long state() {
        return h;
    }

    public Hasher putLong(long v) {
        h = mix(h, v);
        return this;
    }

    public Hasher putInt(int v) {
        return putLong(v);
    }

    public Hasher putBoolean(boolean v) {
        return putLong(v ? 1L : 0L);
    }

    public Hasher putByte(byte v) {
        return putLong(v);
    }

    /** By its bits: every NaN hashes alike, and -0.0 differs from 0.0. */
    public Hasher putDouble(double v) {
        return putLong(Double.doubleToLongBits(v));
    }

    public Hasher putFloat(float v) {
        return putInt(Float.floatToIntBits(v));
    }

    /** The length, then every char. */
    public Hasher putString(String s) {
        putInt(s.length());
        for (int i = 0; i < s.length(); i++) {
            putInt(s.charAt(i));
        }
        return this;
    }

    public long finish() {
        return finish(h);
    }
}
