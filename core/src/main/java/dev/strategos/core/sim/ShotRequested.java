package dev.strategos.core.sim;

import dev.strategos.core.sim.combat.Damage;
import java.util.Objects;

/** UNIT_AI's firing intent; COMBAT freezes the trajectory after MOVEMENT. Handles include their generations. */
public record ShotRequested(int shooter, int target, Damage damage, double range, double speed, double spread)
        implements BusMessage {
    public ShotRequested {
        Objects.requireNonNull(damage);
        if (damage.attacker() != shooter || damage.target() != target) {
            throw new IllegalArgumentException("shot intent handles differ from its packet");
        }
        if (!Double.isFinite(range)
                || !(range > 0)
                || !Double.isFinite(speed)
                || !(speed > 0)
                || !Double.isFinite(spread)
                || spread < 0) {
            throw new IllegalArgumentException("invalid shot intent range, speed or spread");
        }
    }
}
