package dev.strategos.core.sim;

import dev.strategos.core.content.TemplateSet;

/** Test-only access to a core's state, for tests in the kernel's subpackages. */
public final class KernelAccess {
    private KernelAccess() {}

    public static MatchState state(MatchCore core) {
        return ((MatchCoreImpl) core).state();
    }

    /** A core whose journal checkpoints and re-bases at the given limits. */
    public static MatchCore create(
            MatchConfig config, TemplateSet templates, CorePorts ports, int checkpointEvery, int maxJournalTicks) {
        return MatchCoreImpl.create(config, templates, ports, checkpointEvery, maxJournalTicks);
    }

    /** The computer players' part of the state hash, for a test that hunts a divergence. */
    public static long aiHash(MatchState s) {
        return s.aiSlots().mix(0);
    }

    /** The pending commands' count, for the same. */
    public static int pending(MatchState s) {
        return s.pendingCommands().size();
    }
}
