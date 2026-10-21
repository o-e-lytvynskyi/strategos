package dev.strategos.core.sim;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.diag.PhaseTimers;
import dev.strategos.core.shared.CoreApi;

/**
 * The driving port of one match: the mod feeds it a tick's inputs and applies what it returns. T6a adds
 * {@code snapshot}, {@code restore} and {@code journal} (M0 spec 1.6 row 45).
 */
@CoreApi
public interface MatchCore {
    /** {@code in.tick()} must be {@code queries().tick() + 1}, else IllegalArgumentException. */
    TickOutputs step(TickInputs in);

    MatchQueries queries();

    /** The whole state's hash; the match id is excluded, so L1 twins of a GameTest match agree. */
    long stateHash();

    PhaseTimers timers();

    static MatchCore create(MatchConfig config, TemplateSet templates, CorePorts ports) {
        return new MatchCoreImpl(config, templates, ports);
    }
}
