package dev.strategos.core.sim.econ;

import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.MatchState;
import org.jspecify.annotations.Nullable;

/**
 * The economy's checks across stores: the work claims a restored match puts back on its units, and the resource
 * balances FINAL checks each step. Split out of {@link MatchState} in M4 T1, which keeps the calls.
 */
public final class EconomyChecks {
    private EconomyChecks() {}

    /** The claims a restored match's nodes and buildings hold, put back on their units (ECO-05, BLD-03). */
    public static void restoreClaims(MatchState s) {
        for (int handle : s.buildings().handles()) {
            for (int slot = 0; slot < s.buildings().slotCount(handle); slot++) {
                int unit = s.buildings().claim(handle, slot);
                if (unit != 0 && s.entities().isAlive(unit)) {
                    s.carry().setSite(Handle.index(unit), handle);
                }
            }
        }
        for (int id = 1; id <= s.nodes().count(); id++) {
            for (int slot = 0; slot < s.nodes().slotCount(id); slot++) {
                int handle = s.nodes().claim(id, slot);
                if (handle != 0 && s.entities().isAlive(handle)) {
                    s.carry().setClaimed(Handle.index(handle), id);
                }
            }
        }
    }

    /**
     * The first resource balance that does not hold (ECO-05, {@link Ledger}), or null: checked in FINAL each step, the
     * treasury's beside it.
     */
    public static @Nullable String broken(MatchState s) {
        if (s.ledger().empty() && s.carry().empty()) {
            return null;
        }
        for (int r = 0; r < s.ledger().resources(); r++) {
            long gathered = 0;
            for (int f = 0; f < s.treasury().factions(); f++) {
                gathered += s.treasury().gathered(f, r);
            }
            String broken = s.ledger()
                    .broken(
                            r,
                            s.nodes().remaining(r),
                            s.carry().total(r, s.entities().highWater()),
                            gathered);
            if (broken != null) {
                return broken;
            }
        }
        return null;
    }
}
