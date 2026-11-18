package dev.strategos.core.sim.command;

import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.vision.VisionGrid;
import org.jspecify.annotations.Nullable;

/**
 * Whether an issuer may command a unit. A handle never issued, a dead one with a stale generation and, once a vision
 * grid exists (FOW-01), one the issuer cannot see all take the same {@code UNKNOWN_TARGET} branch, so commands cannot
 * probe the fog (CORE-03.4).
 */
final class Ownership {
    private Ownership() {}

    static @Nullable RejectReason check(PlayerSlot issuer, int handle, MatchState s) {
        if (!s.entities().isAlive(handle)) {
            return RejectReason.UNKNOWN_TARGET;
        }
        if (!unitVisible(issuer, handle, s)) return RejectReason.UNKNOWN_TARGET;
        if (!issuer.isAdmin()
                && !s.diplomacy().controlsFaction(issuer, s.identity().faction(Handle.index(handle)))) {
            return RejectReason.NOT_OWNER;
        }
        return null;
    }

    static boolean unitVisible(PlayerSlot issuer, int handle, MatchState s) {
        if (issuer.isAdmin()) return true;
        int primary = s.diplomacy().primaryFaction(issuer);
        if (primary < 0) return false;
        return primary == s.identity().faction(Handle.index(handle))
                || s.visionSystem() == null
                || s.visionSystem().visibility(primary).isVisible(handle);
    }

    /** Visibility precedes authority, so grants cannot probe another faction's hidden buildings. */
    static @Nullable RejectReason building(PlayerSlot issuer, int handle, MatchState s) {
        if (!BuildingStore.isBuilding(handle) || !s.buildings().alive(handle) || !buildingVisible(issuer, handle, s))
            return RejectReason.UNKNOWN_TARGET;
        return issuer.isAdmin()
                        || s.diplomacy().controlsFaction(issuer, s.buildings().faction(handle))
                ? null
                : RejectReason.NOT_OWNER;
    }

    static boolean buildingVisible(PlayerSlot issuer, int handle, MatchState s) {
        if (issuer.isAdmin()) return true;
        int primary = s.diplomacy().primaryFaction(issuer);
        if (primary < 0) return false;
        var play = s.config().rules().play();
        var vision = s.visionSystem();
        if (primary == s.buildings().faction(handle) || play.revealMap() || play.aiVision() || vision == null)
            return true;
        var grid = vision.grid(primary);
        var at = s.footprintOf(handle);
        for (int x = at.anchorX(); x < at.anchorX() + at.spanX(); x++) {
            for (int z = at.anchorZ(); z < at.anchorZ() + at.spanZ(); z++) {
                if (grid.count(VisionGrid.cellOf(x), VisionGrid.cellOf(z)) > 0) return true;
            }
        }
        return false;
    }
}
