package dev.strategos.core.sim.command;

import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.MatchState;
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
        if (!issuer.isAdmin() && issuer.value() != s.identity().faction(Handle.index(handle))) {
            return RejectReason.NOT_OWNER;
        }
        return null;
    }
}
