package dev.strategos.mod.server.net;

import dev.strategos.core.sim.MatchLifecycle;
import org.jspecify.annotations.Nullable;

/** Singleplayer controls change server-wide time, so a published LAN world cannot authorize them. */
final class MatchControlPolicy {
    private MatchControlPolicy() {}

    static boolean allows(boolean singleplayer, boolean published, boolean owner, @Nullable MatchLifecycle lifecycle) {
        return singleplayer
                && !published
                && owner
                && (lifecycle == MatchLifecycle.RUNNING || lifecycle == MatchLifecycle.PAUSED);
    }
}
