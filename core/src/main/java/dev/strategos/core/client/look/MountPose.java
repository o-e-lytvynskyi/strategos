package dev.strategos.core.client.look;

/**
 * The pose of a mounted unit's horse (UNIT-06), in radians and blocks, from what the kinematic driver already gives a
 * body: the limb swing and speed (0 at rest, 1 at a gallop) of vanilla's walk animation, and the progress (0 to 1) of
 * the swing {@code UnitStruck} starts. A gallop swings the legs in pairs, a front pair leading a hind pair, and bobs the
 * body; a strike tosses the head down and rears the forehand, peaking half way and back to rest at both ends. Pure, so
 * the renderer only applies the numbers.
 *
 * @param frontLeft the front left leg's pitch
 * @param frontRight the front right leg's pitch
 * @param hindLeft the hind left leg's pitch
 * @param hindRight the hind right leg's pitch
 * @param head the head's pitch added to its resting angle
 * @param tail the tail's pitch added to its resting angle
 * @param bodyPitch the body's pitch; negative rears the forehand
 * @param bob the rider's and body's lift in blocks
 */
public record MountPose(
        float frontLeft,
        float frontRight,
        float hindLeft,
        float hindRight,
        float head,
        float tail,
        float bodyPitch,
        float bob) {
    /** The leg swing at a full gallop, radians: wider than a walk's. */
    static final float LEG_SWING = 1.0F;

    /** How far the right leg of a pair trails the left, radians: a canter's uneven beat. */
    static final float TRAIL = 0.4F;

    /** The head's toss on a strike, radians. */
    static final float TOSS = 0.5F;

    /** The forehand's rear on a strike, radians. */
    static final float REAR = 0.2F;

    /** The body's lift at a gallop's top, blocks. */
    static final float BOUNCE = 0.06F;

    /** The tail streaming out with speed, radians. */
    static final float STREAM = 0.75F;

    /**
     * The pose at {@code limbSwing} (vanilla's accumulated stride), {@code speed} (clamped to 0..1) and {@code
     * strike} (progress of the attack swing, 0 when none; values outside 0..1 count as none).
     */
    public static MountPose of(float limbSwing, float speed, float strike) {
        float s = Math.clamp(speed, 0F, 1F);
        float phase = limbSwing * 0.6662F;
        float swing = LEG_SWING * s;
        float bump = strike > 0F && strike < 1F ? (float) Math.sin(strike * Math.PI) : 0F;
        return new MountPose(
                (float) Math.cos(phase) * swing,
                (float) Math.cos(phase + TRAIL) * swing,
                (float) Math.cos(phase + Math.PI) * swing,
                (float) Math.cos(phase + Math.PI + TRAIL) * swing,
                bump * TOSS + (float) Math.cos(phase * 2) * 0.1F * s,
                STREAM * s,
                -bump * REAR,
                (float) Math.abs(Math.cos(phase)) * BOUNCE * s);
    }
}
