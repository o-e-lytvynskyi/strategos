package dev.strategos.core.sim.nav;

import dev.strategos.core.sim.Allies;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.spatial.UnitFilter;

/**
 * The door state of a gate (PATH-12): open while a unit of a faction allied with the gate's owner stands within
 * {@value #RANGE} blocks of any of its columns. It only drives the gate's blocks; what may cross is {@link GateLinks}
 * alone, so an open door never lets an enemy through.
 */
public final class GateDoors {
    public static final double RANGE = 3.0;

    private GateDoors() {}

    /** Whether gate {@code id} is open now (the spatial grid as of the last MOVEMENT). */
    public static boolean open(MatchState s, int id) {
        int owner = s.gates().owner(id);
        Allies allies = s.allies();
        int mask = 0;
        for (int f = 0; f < s.gaia(); f++) {
            if (allies.allied(f, owner)) {
                mask |= 1 << f;
            }
        }
        if (mask == 0) {
            return false;
        }
        UnitFilter filter = new UnitFilter(mask, 0);
        for (int c : s.gates().columns(id)) {
            if (s.spatial().countWithin((c >>> 16) + 0.5, (c & 0xFFFF) + 0.5, RANGE, filter) > 0) {
                return true;
            }
        }
        return false;
    }
}
