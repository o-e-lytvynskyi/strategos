package dev.strategos.core.sim;

/** {@code Rng} stream ids, one per purpose, so draws for one purpose never shift another's. */
public final class Streams {
    public static final int SPAWN = 1;
    public static final int SHOT = 2;

    private Streams() {}
}
