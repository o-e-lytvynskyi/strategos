package dev.strategos.core.sim.civ;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.econ.CarryStore;

/**
 * What a gatherer does with a load that is full, or all it will get from a spent node (ADR-0028), asked at
 * {@code Gathering}'s one point where a load turns toward a dropsite. The default credits nothing and so sends the unit
 * to the nearest dropsite that takes the load; {@link #DIRECT} credits it to the treasury where the unit stands. Stateless:
 * a variant keeps nothing per unit.
 */
@CoreApi
@FunctionalInterface
public interface DeliveryRule {
    /** Today's behaviour: the load is carried to a dropsite. */
    DeliveryRule DEFAULT = (s, unit) -> false;

    /** The load is credited to the faction's treasury as {@code gathered} where the unit stands, and the carry emptied. */
    DeliveryRule DIRECT = (s, unit) -> {
        int resource = s.carry().resource(unit);
        if (resource == CarryStore.NONE) {
            return false;
        }
        long load = s.carry().clear(unit);
        s.treasury().gather(s.identity().faction(unit), resource, load);
        return true;
    };

    /**
     * Credits unit {@code unit}'s load in place and returns true, or returns false and leaves the load: the unit then goes
     * to a dropsite.
     */
    boolean creditInPlace(MatchState s, int unit);
}
