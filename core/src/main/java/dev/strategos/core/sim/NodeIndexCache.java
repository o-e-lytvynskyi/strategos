package dev.strategos.core.sim;

import dev.strategos.core.sim.econ.NodeIndex;
import org.jspecify.annotations.Nullable;

/** The node index, built on first use after discovery, a restore or a node added; derived, neither hashed nor saved. */
final class NodeIndexCache {
    private final MatchState state;
    private @Nullable NodeIndex index;

    NodeIndexCache(MatchState state) {
        this.state = state;
    }

    void invalidate() {
        index = null;
    }

    NodeIndex get() {
        NodeIndex built = index;
        if (built == null) {
            long start = state.economyTimes().start();
            built = new NodeIndex(
                    state.nodes(),
                    state.config().rules().economy(),
                    state.surfaces().pagesX(),
                    state.surfaces().pagesZ());
            index = built;
            state.economyTimes().built(start);
        }
        return built;
    }
}
