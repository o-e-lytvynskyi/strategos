package dev.strategos.core.shared;

/**
 * Stateless counter-based randomness (ADR-0009): every draw is a pure function of its coordinates, so there is no state
 * to save and no draw shifts another.
 */
public final class Rng {
    public static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private Rng() {}

    /** The SplitMix64 finaliser. */
    public static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static long u64(long seed, int stream, long tick, long key, long salt) {
        long z = mix64(seed + GOLDEN_GAMMA * (stream + 1L));
        z = mix64(z ^ tick);
        z = mix64(z ^ (key * GOLDEN_GAMMA));
        return mix64(z ^ (salt + 0x632BE59BD9B4E019L));
    }

    /** Uniform in [0, 1), from the top 53 bits. */
    public static double unit(long seed, int stream, long tick, long key, long salt) {
        return (u64(seed, stream, tick, key, salt) >>> 11) * 0x1.0p-53;
    }

    /** Uniform in [0, bound), by Lemire's multiply-shift on the top 32 bits. */
    public static int below(long seed, int stream, long tick, long key, long salt, int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound " + bound + " must be positive");
        }
        return (int) (((u64(seed, stream, tick, key, salt) >>> 32) * bound) >>> 32);
    }
}
