package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;
import java.util.List;

/**
 * The agents in the match space this tick (04 §9.5): journaled, and read by avoidance in {@code MOVEMENT}. It holds
 * for its tick only, so a tick without one has no agents and nothing about them is saved or hashed.
 */
@CoreApi
public record AgentUpdate(List<Agent> agents) implements InputEvent {
    /** The most agents one tick may carry. */
    public static final int MAX_AGENTS = 64;

    public AgentUpdate {
        agents = List.copyOf(agents);
        if (agents.size() > MAX_AGENTS) {
            throw new IllegalArgumentException(agents.size() + " agents in one tick, more than " + MAX_AGENTS);
        }
    }
}
