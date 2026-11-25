package dev.strategos.core.sim;

import dev.strategos.core.sim.ability.AbilityStore;
import dev.strategos.core.sim.capture.CaptureStore;
import dev.strategos.core.sim.nav.GateOverlay;
import dev.strategos.core.sim.territory.TerritorySystem;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The play state of a match that the territory, capture and defence rules own: what is being captured, the gates of
 * palisades and walls, what the units have breached, the abilities' cooldowns and the territory system. It belongs to one {@link MatchState}
 * and is reached through {@link MatchState#play()}; the match's own columns are blocked and released through it.
 */
public final class PlayState {
    private final MatchState owner;
    private final CaptureStore capture;
    private final GateOverlay gates;
    private final BreachLog breaches = new BreachLog(); // saved and hashed (Play.breaches)
    private final AbilityStore abilities = new AbilityStore(); // saved and hashed while it holds anything (UNIT-10)
    private @Nullable TerritorySystem territorySystem;

    PlayState(MatchState owner, MatchConfig config) {
        this.owner = owner;
        this.capture = new CaptureStore(config.factions());
        this.gates = new GateOverlay(
                config.factions(), config.bounds().sizeX(), config.bounds().sizeZ());
    }

    /** The abilities' cooldowns and timed modifiers (UNIT-10). */
    public AbilityStore abilities() {
        return abilities;
    }

    /** What the units have breached lately (BLD-13). */
    public BreachLog breaches() {
        return breaches;
    }

    /** The gates of palisades and walls (PATH-12). */
    public GateOverlay gates() {
        return gates;
    }

    /** Adds a gate of {@code owner} over {@code columns} ({@code x << 16 | z}), blocking them for everyone; its id. */
    public int addGate(int owner, int[] columns) {
        int id = gates.add(owner, columns); // validates first: nothing is blocked for a bad gate
        for (int c : columns) {
            this.owner.blockColumn(c >>> 16, c & 0xFFFF);
        }
        return id;
    }

    /** Removes gate {@code id} and releases its columns. */
    public void removeGate(int id) {
        for (int c : gates.columns(id)) {
            owner.releaseColumn(c >>> 16, c & 0xFFFF);
        }
        gates.remove(id);
    }

    /** A snapshot's gates, their columns blocked again, before the pages are published. */
    void restoreGates(List<Long> saved) {
        gates.restore(saved);
        for (int id : gates.ids()) {
            for (int c : gates.columns(id)) {
                owner.restoreColumn(c >>> 16, c & 0xFFFF);
            }
        }
    }

    /** The buildings being captured (CMB-10). */
    public CaptureStore capture() {
        return capture;
    }

    /** Territory (TERR-01), once the core has made it. */
    public @Nullable TerritorySystem territorySystem() {
        return territorySystem;
    }

    void setTerritorySystem(TerritorySystem territory) {
        this.territorySystem = territory;
    }
}
