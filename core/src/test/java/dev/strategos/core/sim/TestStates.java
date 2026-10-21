package dev.strategos.core.sim;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.diag.MatchLog;
import dev.strategos.core.shared.MatchId;

/** Bare match states for the kernel's own tests. */
final class TestStates {
    private TestStates() {}

    static MatchState empty() {
        MatchConfig config =
                new MatchConfig(new MatchId(1), 42, new MatchBounds(32, 32, 0, 16, 1.0), 2, MatchRules.DEFAULTS);
        return new MatchState(config, TemplateSet.builtin(), new EntityStore(), MatchLog.forSim(config.id()));
    }
}
