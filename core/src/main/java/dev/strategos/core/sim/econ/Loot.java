package dev.strategos.core.sim.econ;

import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.stats.StatService;

/**
 * What a kill pays (CMB-13, M10 spec §2.10): the killer's faction receives the victim's {@code Loot/resources/<r>} as
 * a source of its own, counted in the treasury's {@code looted}, plus whatever a unit victim was carrying, which moves
 * from the victim's load to the killer's stock as gathered income (so the resource ledger's carried + gathered +
 * dropped still balances: nothing is created or lost by the move). Nothing is paid for a kill by Gaia, by the victim's
 * own faction or an ally, or by nobody. Allocation-free.
 */
public final class Loot {
    /** No faction: the kill had no killer that counts. */
    public static final int NONE = -1;

    private Loot() {}

    /**
     * The faction a kill by {@code attacker} pays, {@link #NONE} when {@code attacker} is nobody or a gone unit; a
     * building's owner for a building. Read at the moment of the blow, while the attacker is still alive.
     */
    public static int killer(MatchState s, int attacker) {
        if (attacker == Handle.NONE) {
            return NONE;
        }
        if (BuildingStore.isBuilding(attacker)) {
            return s.buildings().alive(attacker) ? s.buildings().faction(attacker) : NONE;
        }
        return s.entities().isAlive(attacker) ? s.identity().faction(Handle.index(attacker)) : NONE;
    }

    /** Unit {@code victim} (still in the match) died to {@code killer}'s faction: pays its loot and its carry. */
    public static void unit(MatchState s, int killer, int victim) {
        int i = Handle.index(victim);
        int owner = s.identity().faction(i);
        if (!pays(s, killer, owner)) {
            return;
        }
        StatService stats = s.stats();
        Costs costs = s.costs();
        for (int r = 0; r < s.treasury().resources(); r++) {
            int path = costs.lootPath(r);
            if (path >= 0) {
                pay(s, killer, r, stats.read(i, path));
            }
        }
        int resource = s.carry().resource(i);
        if (resource != CarryStore.NONE) {
            long carried = s.carry().clear(i);
            if (carried > 0) {
                s.treasury().gather(killer, resource, carried);
            }
        }
    }

    /** Building {@code building} fell to {@code killer}'s faction: pays its loot. */
    public static void building(MatchState s, int killer, int building) {
        int owner = s.buildings().faction(building);
        if (!pays(s, killer, owner)) {
            return;
        }
        int template = s.buildings().template(building);
        StatService stats = s.stats();
        Costs costs = s.costs();
        for (int r = 0; r < s.treasury().resources(); r++) {
            int path = costs.lootPath(r);
            if (path >= 0) {
                pay(s, killer, r, stats.blockValue(template, owner, path));
            }
        }
    }

    private static boolean pays(MatchState s, int killer, int owner) {
        return killer != NONE && s.allies().enemies(killer, owner, s.config().factions());
    }

    private static void pay(MatchState s, int killer, int resource, double units) {
        if (Double.isFinite(units) && units > 0) {
            long amount = Amount.of(units);
            if (amount > 0) {
                s.treasury().loot(killer, resource, amount);
            }
        }
    }
}
