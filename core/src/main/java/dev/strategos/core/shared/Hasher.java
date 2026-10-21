package dev.strategos.core.shared;

/** An order-sensitive 64-bit hash for state hashes; equal sequences of puts give equal hashes on every platform. */
public final class Hasher {
    private long h;

    public Hasher(long seed) {
        h = Rng.mix64(seed ^ 0x5354524154454753L);
    }

    public Hasher putLong(long v) {
        h = Rng.mix64(h ^ Rng.mix64(v + Rng.GOLDEN_GAMMA));
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
        return Rng.mix64(h);
    }
}
