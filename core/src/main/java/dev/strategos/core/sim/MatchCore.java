package dev.strategos.core.sim;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.diag.PhaseTimers;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.TerritoryGrid;
import dev.strategos.core.sim.civ.RuleCatalogue;
import dev.strategos.core.sim.journal.Journal;
import dev.strategos.core.sim.vision.VisionSystem;
import org.jspecify.annotations.Nullable;

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

    default @Nullable VisionSystem vision() {
        return null;
    }

    /** The delivered territory grid (TERR-01), or null before the first one. */
    default @Nullable TerritoryGrid territory() {
        return null;
    }

    /** How many territory grids were delivered: a new version for the clients whenever it moves (M9 spec §2.4). */
    default long territoryVersion() {
        return 0;
    }

    static MatchCore create(MatchConfig config, TemplateSet templates, CorePorts ports) {
        return create(config, templates, RuleCatalogue.DEFAULT, ports);
    }

    /**
     * As {@link #create(MatchConfig, TemplateSet, CorePorts)}, the civilisations' rule variants drawn from {@code
     * catalogue}: production always passes {@link RuleCatalogue#DEFAULT}; the same catalogue must serve a restore and a
     * replay of the match.
     */
    static MatchCore create(MatchConfig config, TemplateSet templates, RuleCatalogue catalogue, CorePorts ports) {
        return MatchCoreImpl.create(
                config,
                templates,
                catalogue,
                ports,
                Journal.DEFAULT_CHECKPOINT_EVERY,
                ports.journal().keepTicks());
    }

    /**
     * A match continued from a snapshot. A unit whose template the set lacks throws, naming the unit; a different
     * template-set hash is allowed and logged.
     */
    static MatchCore restore(MatchSnapshot snapshot, TemplateSet templates, CorePorts ports) {
        return restore(snapshot, templates, ports, SliceSource.NONE);
    }

    /** As {@link #restore(MatchSnapshot, TemplateSet, CorePorts)}, its terrain pages rebuilt from {@code slices}. */
    static MatchCore restore(MatchSnapshot snapshot, TemplateSet templates, CorePorts ports, SliceSource slices) {
        return restore(snapshot, templates, RuleCatalogue.DEFAULT, ports, slices);
    }

    /** As {@link #restore(MatchSnapshot, TemplateSet, CorePorts, SliceSource)}, the rules drawn from {@code catalogue}. */
    static MatchCore restore(
            MatchSnapshot snapshot,
            TemplateSet templates,
            RuleCatalogue catalogue,
            CorePorts ports,
            SliceSource slices) {
        return MatchCoreImpl.restore(
                snapshot,
                templates,
                catalogue,
                ports,
                slices,
                Journal.DEFAULT_CHECKPOINT_EVERY,
                ports.journal().keepTicks());
    }
}
