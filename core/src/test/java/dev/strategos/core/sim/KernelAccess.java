package dev.strategos.core.sim;

/** Test-only access to a core's state, for tests in the kernel's subpackages. */
public final class KernelAccess {
    private KernelAccess() {}

    public static MatchState state(MatchCore core) {
        return ((MatchCoreImpl) core).state();
    }
}
