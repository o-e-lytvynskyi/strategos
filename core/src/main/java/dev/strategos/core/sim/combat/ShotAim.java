package dev.strategos.core.sim.combat;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.Rng;
import dev.strategos.core.sim.Streams;

/** Analytic horizontal lead and isotropic Gaussian spread, frozen at release (CMB-04). */
public final class ShotAim {
    private ShotAim() {}

    @CoreApi
    public record Aim(long flightTicks, long landTick, double landingX, double landingY, double landingZ) {}

    public static Aim aim(long seed, long tick, long id, ShotStore.Launch launch) {
        if (tick < 0 || id <= 0 || id > (Long.MAX_VALUE - 1) / 2) {
            throw new IllegalArgumentException("invalid shot tick or random counter");
        }
        double d = DetMath.length(launch.targetX() - launch.fromX(), launch.targetZ() - launch.fromZ());
        double flight = Math.ceil(d / launch.speed());
        if (!Double.isFinite(flight) || flight >= 0x1.0p63) {
            throw new IllegalArgumentException("shot flight exceeds the tick range");
        }
        long ticks = (long) flight;
        long landTick;
        try {
            landTick = Math.addExact(tick, ticks);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("shot landing tick overflow", overflow);
        }
        double x = launch.targetX() + launch.targetVx() * ticks;
        double z = launch.targetZ() + launch.targetVz() * ticks;
        if (launch.spread() != 0 && d != 0) {
            double sigma = launch.spread() * d / 100;
            double u1 = 1 - Rng.unit(seed, Streams.SHOT, tick, launch.damage().attacker(), 2 * id);
            double u2 = Rng.unit(seed, Streams.SHOT, tick, launch.damage().attacker(), 2 * id + 1);
            double radius = sigma * DetMath.sqrt(-2 * DetMath.log(u1));
            double angle = 2 * Math.PI * u2;
            x += radius * DetMath.cos(angle);
            z += radius * DetMath.sin(angle);
        }
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("nonfinite shot landing point");
        }
        return new Aim(ticks, landTick, x, launch.targetY(), z);
    }
}
