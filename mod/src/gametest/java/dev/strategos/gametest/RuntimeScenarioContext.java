package dev.strategos.gametest;

import dev.strategos.core.protocol.Command;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.UnitStateChanged;
import dev.strategos.core.testkit.ScenarioContext;
import dev.strategos.mod.server.match.MatchRuntime;
import java.util.ArrayList;
import java.util.List;

/**
 * A scenario's view of a live match in the world, the GameTest counterpart of {@code SimHarness}: commands go through
 * the match's inbox, and results and state changes are collected from its outputs. Server thread only.
 */
public final class RuntimeScenarioContext implements ScenarioContext {
    private final MatchRuntime rt;
    private final List<CommandResult> results = new ArrayList<>();
    private final List<UnitStateChanged> stateChanges = new ArrayList<>();

    /** Listens from now on; make it before the match's first step. */
    public RuntimeScenarioContext(MatchRuntime rt) {
        this.rt = rt;
        rt.addListener(out -> {
            for (OutputEvent event : out.events()) {
                if (event instanceof CommandResult result) {
                    results.add(result);
                } else if (event instanceof UnitStateChanged change) {
                    stateChanges.add(change);
                }
            }
        });
    }

    @Override
    public long tick() {
        return core().queries().tick();
    }

    @Override
    public MatchQueries queries() {
        return core().queries();
    }

    @Override
    public int submit(PlayerSlot issuer, Command command) {
        return rt.submit(issuer, command);
    }

    @Override
    public List<CommandResult> results() {
        return List.copyOf(results);
    }

    @Override
    public List<UnitStateChanged> stateChanges() {
        return List.copyOf(stateChanges);
    }

    @Override
    public long stateHash() {
        return core().stateHash();
    }

    private MatchCore core() {
        MatchCore core = rt.core();
        if (core == null) {
            throw new IllegalStateException("match " + rt.id() + " has no core");
        }
        return core;
    }
}
