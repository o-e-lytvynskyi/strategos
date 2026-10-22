package dev.strategos.core.sim.move;

/**
 * Separation steering for a unit in a crowd (04 §9.5), where ORCA's lines would cost the most: the preferred velocity
 * plus a push away from each neighbour within {@value #RANGE} blocks, from nothing at that range to the unit's speed at
 * contact, the sum clamped to that speed. The pushes are summed in the caller's order, so the result is deterministic.
 */
final class Separation {
    static final double RANGE = 1.5;

    private double x;
    private double z;
    private double speed;

    void start(double prefX, double prefZ, double speed) {
        this.x = prefX;
        this.z = prefZ;
        this.speed = speed;
    }

    /**
     * A neighbour at (relX, relZ) from the unit; at the unit's own point it pushes along +X when {@code east} and -X
     * otherwise, so two units on one point part.
     */
    void push(double relX, double relZ, boolean east) {
        double distance = Math.sqrt(relX * relX + relZ * relZ);
        if (distance >= RANGE) {
            return;
        }
        double weight = (RANGE - distance) / RANGE * speed;
        if (distance == 0) {
            x += east ? weight : -weight;
            return;
        }
        x -= relX / distance * weight;
        z -= relZ / distance * weight;
    }

    double x() {
        double length = Math.sqrt(x * x + z * z);
        return length > speed ? x / length * speed : x;
    }

    double z() {
        double length = Math.sqrt(x * x + z * z);
        return length > speed ? z / length * speed : z;
    }
}
