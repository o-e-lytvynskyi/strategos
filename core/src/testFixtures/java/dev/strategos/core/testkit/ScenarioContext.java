package dev.strategos.core.testkit;

import dev.strategos.core.protocol.Command;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.TerrainPage;
import dev.strategos.core.sim.UnitStateChanged;
import java.util.List;

/** What a scenario sees of the match it runs in: L1's {@link SimHarness}, or the GameTest runtime (T8). */
public interface ScenarioContext {
    long tick();

    MatchQueries queries();

    /** Queues a command for the next tick; returns its client sequence number. */
    int submit(PlayerSlot issuer, Command command);

    /** Every command result since the match started. */
    List<CommandResult> results();

    /** Every unit state change since the match started. */
    List<UnitStateChanged> stateChanges();

    long stateHash();

    /** Queues a terrain page for the next tick; only L1 takes terrain this way, a GameTest from its world. */
    default void publish(TerrainPage page) {
        throw new UnsupportedOperationException("this match takes its terrain from the world");
    }
}
