package dev.strategos.core.sim.combat;

import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.build.BuildingStore;

/** Frozen attack provenance and the final locked-team guard; legacy unlocked combat remains unchanged. */
public final class DamageOrigins {
    private DamageOrigins() {}

    public static Damage captureQueued(MatchState state, Damage damage) {
        // Unattributed lethal packets are trusted structural/world consequences, not hostile attacks.
        return damage.lethal() ? damage : captureAttack(state, damage);
    }

    public static Damage captureAttack(MatchState state, Damage damage) {
        if (!state.diplomacy().teamsLocked() || damage.sourceFaction() != Damage.UNATTRIBUTED) return damage;
        int faction = owner(state, damage.attacker());
        return faction == Damage.UNATTRIBUTED ? damage : damage.withSourceFaction(faction);
    }

    public static boolean permits(MatchState state, Damage damage) {
        if (!state.diplomacy().teamsLocked()) return true;
        if (damage.sourceFaction() == Damage.UNATTRIBUTED) {
            return damage.attacker() == Handle.NONE || damage.lethal();
        }
        return !state.diplomacy().alliedFactions(damage.sourceFaction(), owner(state, damage.target()));
    }

    private static int owner(MatchState state, int handle) {
        if (handle == Handle.NONE) return Damage.UNATTRIBUTED;
        if (BuildingStore.isBuilding(handle)) {
            return state.buildings().alive(handle) ? state.buildings().faction(handle) : Damage.UNATTRIBUTED;
        }
        return state.entities().isAlive(handle) ? state.identity().faction(Handle.index(handle)) : Damage.UNATTRIBUTED;
    }
}
