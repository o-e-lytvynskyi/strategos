package dev.strategos.core.sim.move;

/**
 * One ORCA constraint (RVO2's {@code Line}): the velocities on the left of {@code direction}, a unit vector, through
 * {@code point} are allowed. Mutable and pooled by {@link Orca}, so a solve allocates nothing.
 */
final class OrcaLine {
    double pointX;
    double pointZ;
    double directionX;
    double directionZ;

    void set(double pointX, double pointZ, double directionX, double directionZ) {
        this.pointX = pointX;
        this.pointZ = pointZ;
        this.directionX = directionX;
        this.directionZ = directionZ;
    }

    void copy(OrcaLine other) {
        set(other.pointX, other.pointZ, other.directionX, other.directionZ);
    }
}
