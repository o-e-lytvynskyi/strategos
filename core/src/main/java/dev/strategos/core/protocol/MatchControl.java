package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;

/**
 * The {@code match_control} payload (MATCH-05, D-18), client to server: a seated player pauses or resumes its match, or
 * sets the game's speed to one of {@link #SLOW}, {@link #NORMAL} and {@link #FAST}; in singleplayer only. Pausing stops the core's steps while its inbox
 * keeps buffering; the speed is the server's tick rate, never a tick freeze.
 */
@CoreApi
public record MatchControl(int action, float speed) {
    public static final int PAUSE = 0;
    public static final int RESUME = 1;
    public static final int SPEED = 2;

    /** The speeds offered: half, normal and half again. */
    public static final float SLOW = 0.5F;

    public static final float NORMAL = 1.0F;
    public static final float FAST = 1.5F;

    public static final MatchControl PAUSE_NOW = new MatchControl(PAUSE, 1);
    public static final MatchControl RESUME_NOW = new MatchControl(RESUME, 1);

    public MatchControl {
        if (action < PAUSE || action > SPEED) {
            throw new IllegalArgumentException("no match control " + action);
        }
    }

    /** Whether {@code speed} is one of those offered. */
    public static boolean offered(float speed) {
        return speed == SLOW || speed == NORMAL || speed == FAST;
    }

    public static final WireCodec<MatchControl> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, MatchControl c) {
            out.writeByte(c.action()).writeFloat(c.speed());
        }

        @Override
        public MatchControl read(WireReader in) {
            int action = in.readUnsignedByte();
            if (action > SPEED) {
                throw new WireException("unknown match control " + action);
            }
            return new MatchControl(action, in.readFloat());
        }

        @Override
        public int maxSize() {
            return 1 + 4;
        }
    };
}
