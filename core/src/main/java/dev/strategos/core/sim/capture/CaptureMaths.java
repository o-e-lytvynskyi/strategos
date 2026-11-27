package dev.strategos.core.sim.capture;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.sim.Allies;

/**
 * One second of a building's capture (M9 spec §2.6), in integer thousandths of a point. Capture first: each attacking
 * faction's effect — its units' summed strength, divided by {@code 0.1 + 0.9 × health ÷ max} — moves points from the
 * owner to it; once the owner holds none, the building goes to the faction holding the most (ties to the lowest
 * index), never on a mere majority. Then regeneration moves up to {@code regen} back to the owner from the others, in
 * faction order. Allies of the owner never capture it.
 */
@CoreApi
public final class CaptureMaths {
    private CaptureMaths() {}

    /**
     * The capture effect of {@code strength} (thousandths per second) on a building at {@code health} of {@code max}:
     * {@code strength × 10 max ÷ (max + 9 health)}, rounded down.
     */
    public static long effect(long strength, double health, double max) {
        if (strength <= 0 || max <= 0) {
            return 0;
        }
        double h = Math.max(0, Math.min(max, health));
        return (long) Math.floor(strength * 10.0 * max / (max + 9.0 * h));
    }

    /**
     * Applies one second to {@code points} (per faction, thousandths; summing to the building's total) owned by {@code
     * owner}, with {@code effect[f]} the capture effect of faction {@code f} this second and {@code regen} the owner's
     * regeneration in thousandths; returns the faction that owns it afterwards.
     */
    public static int second(long[] points, int owner, long[] effect, long regen, Allies allies) {
        for (int f = 0; f < points.length && points[owner] > 0; f++) {
            if (f == owner || effect[f] <= 0 || allies.allied(f, owner)) {
                continue;
            }
            long take = Math.min(effect[f], points[owner]);
            points[owner] -= take;
            points[f] += take;
        }
        if (points[owner] <= 0) {
            int best = -1;
            for (int f = 0; f < points.length; f++) {
                if (f != owner && (best < 0 || points[f] > points[best])) {
                    best = f;
                }
            }
            if (best >= 0 && points[best] > 0) {
                return best; // the new owner; the caller gives it every point
            }
        }
        long left = Math.max(0, regen);
        for (int f = 0; f < points.length && left > 0; f++) {
            if (f == owner || points[f] <= 0) {
                continue;
            }
            long back = Math.min(left, points[f]);
            points[f] -= back;
            points[owner] += back;
            left -= back;
        }
        return owner;
    }
}
