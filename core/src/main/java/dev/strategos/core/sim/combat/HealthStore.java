package dev.strategos.core.sim.combat;

import java.util.Arrays;

/**
 * Per index, the damage a unit has taken (CMB-01): its hit points are its most less this, so a unit never hurt is 0,
 * which hashes and saves as nothing, and a change of its most keeps the damage.
 */
public final class HealthStore {
    private double[] damage = new double[0];

    public void ensureCapacity(int size) {
        if (damage.length < size) {
            damage = Arrays.copyOf(damage, Math.max(size, damage.length * 2));
        }
    }

    public double damage(int index) {
        return damage[index];
    }

    public void setDamage(int index, double taken) {
        damage[index] = taken;
    }

    public void reset(int index) {
        damage[index] = 0;
    }
}
