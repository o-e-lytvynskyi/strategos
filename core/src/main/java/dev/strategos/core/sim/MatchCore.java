package dev.strategos.core.sim;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.diag.PhaseTimers;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.sim.journal.Journal;

/** The driving port of one match: the mod feeds it a tick's inputs and applies what it returns. */
@CoreApi
public interface MatchCore {
    /** {@code in.tick()} must be {@code queries().tick() + 1}, else IllegalArgumentException. */
    TickOutputs step(TickInputs in);

    MatchQueries queries();

    /** The whole state's hash; the match id is excluded, so L1 twins of a GameTest match agree. */
    long stateHash();

    /** The match at its last completed tick. */
    MatchSnapshot snapshot();

    /** A read-only copy of the in-memory journal. */
    Journal journal();

    PhaseTimers timers();

    static MatchCore create(MatchConfig config, TemplateSet templates, CorePorts ports) {
        return MatchCoreImpl.create(
                config, templates, ports, Journal.DEFAULT_CHECKPOINT_EVERY, Journal.DEFAULT_MAX_TICKS);
    }

    /**
     * A match continued from a snapshot. A unit whose template the set lacks throws, naming the unit; a different
     * template-set hash is allowed and logged.
     */
    static MatchCore restore(MatchSnapshot snapshot, TemplateSet templates, CorePorts ports) {
        return MatchCoreImpl.restore(
                snapshot, templates, ports, Journal.DEFAULT_CHECKPOINT_EVERY, Journal.DEFAULT_MAX_TICKS);
    }
}
